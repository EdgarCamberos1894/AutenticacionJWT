package com.cambers.auth.email.outbox;

import com.cambers.auth.email.TransactionalEmail;
import com.cambers.auth.repository.AuthSessionRepository;
import com.cambers.auth.repository.OneTimeTokenRepository;
import com.cambers.auth.repository.RefreshTokenRepository;
import com.cambers.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class EmailOutboxIntegrationTests {

    private static final String EMAIL = "outbox@example.com";
    private static final String PASSWORD = "correct horse battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private EmailOutboxRepository outboxRepository;
    @Autowired private EmailOutboxCrypto outboxCrypto;
    @Autowired private OneTimeTokenRepository oneTimeTokenRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private AuthSessionRepository authSessionRepository;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        outboxRepository.deleteAllInBatch();
        oneTimeTokenRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        authSessionRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void registrationCommitsOneTimeTokenAndEncryptedOutboxAtomically() throws Exception {
        register();

        var token = oneTimeTokenRepository.findAll().getFirst();
        EmailOutboxMessage message = outboxRepository.findById(token.getId()).orElseThrow();

        assertThat(message.getStatus()).isEqualTo(EmailOutboxStatus.PENDING);
        assertThat(message.getDeliveryStatus()).isEqualTo(EmailDeliveryStatus.QUEUED);
        assertThat(message.getPurpose()).isEqualTo(EmailOutboxPurpose.EMAIL_VERIFICATION);
        assertThat(message.getAttemptCount()).isZero();
        assertThat(message.getProviderMessageId()).isNull();

        String persistedCiphertext = new String(message.getCiphertext(), StandardCharsets.UTF_8);
        assertThat(persistedCiphertext).doesNotContain(EMAIL, "token=");

        TransactionalEmail email = outboxCrypto.decrypt(message);
        assertThat(email.recipient()).isEqualTo(EMAIL);
        assertThat(email.idempotencyKey()).isEqualTo("auth/email-verification/" + token.getId());
        assertThat(email.text()).contains("token=");
    }

    @Test
    void resendCancelsAndScrubsSupersededVerificationEmail() throws Exception {
        register();
        var originalToken = oneTimeTokenRepository.findAll().getFirst();

        mockMvc.perform(post("/api/v1/auth/email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(EMAIL)))
                .andExpect(status().isNoContent());

        EmailOutboxMessage originalMessage = outboxRepository.findById(originalToken.getId()).orElseThrow();
        assertThat(originalMessage.getStatus()).isEqualTo(EmailOutboxStatus.CANCELLED);
        assertThat(originalMessage.getDeliveryStatus()).isEqualTo(EmailDeliveryStatus.CANCELLED);
        assertThat(originalMessage.getNonce()).isNull();
        assertThat(originalMessage.getCiphertext()).isNull();

        assertThat(outboxRepository.findAll())
                .filteredOn(message -> message.getStatus() == EmailOutboxStatus.PENDING)
                .singleElement()
                .satisfies(message -> assertThat(message.getId()).isNotEqualTo(originalToken.getId()));
    }

    @Test
    void rejectedDuplicateRegistrationDoesNotCreateAnotherOutboxMessage() throws Exception {
        register();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andExpect(status().isConflict());

        assertThat(oneTimeTokenRepository.count()).isEqualTo(1);
        assertThat(outboxRepository.count()).isEqualTo(1);
    }

    private void register() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody()))
                .andExpect(status().isCreated());
    }

    private String registerBody() {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, PASSWORD);
    }
}
