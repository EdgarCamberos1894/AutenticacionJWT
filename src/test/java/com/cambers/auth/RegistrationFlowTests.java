package com.cambers.auth;

import com.cambers.auth.account.internal.model.AccountStatus;
import com.cambers.auth.account.internal.model.OneTimeToken;
import com.cambers.auth.account.internal.model.TokenPurpose;
import com.cambers.auth.account.internal.model.User;
import com.cambers.auth.account.internal.persistence.OneTimeTokenRepository;
import com.cambers.auth.account.internal.persistence.UserRepository;
import com.cambers.auth.authentication.internal.persistence.AuthSessionRepository;
import com.cambers.auth.authentication.internal.persistence.RefreshTokenRepository;
import com.cambers.auth.security.token.GeneratedOpaqueToken;
import com.cambers.auth.security.token.SecureOpaqueTokenGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationFlowTests {

    private static final String EMAIL = "registration@example.com";
    private static final String PASSWORD = "correct horse battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private AuthSessionRepository authSessionRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private OneTimeTokenRepository oneTimeTokenRepository;
    @Autowired private SecureOpaqueTokenGenerator opaqueTokenGenerator;

    @BeforeEach
    void cleanDatabase() {
        oneTimeTokenRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        authSessionRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void registrationCreatesPendingAccountAndHashedVerificationToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(EMAIL, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.emailVerificationRequired").value(true))
                .andExpect(jsonPath("$.userId").isNotEmpty());

        User user = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        assertThat(user.getStatus()).isEqualTo(AccountStatus.PENDING_VERIFICATION);
        assertThat(user.isEmailVerified()).isFalse();
        assertThat(user.getPasswordHash()).startsWith("{argon2id}");

        assertThat(oneTimeTokenRepository.findAll()).singleElement().satisfies(token -> {
            assertThat(token.getPurpose()).isEqualTo(TokenPurpose.VERIFY_EMAIL);
            assertThat(token.getTokenHash()).hasSize(64);
            assertThat(token.isConsumed()).isFalse();
            assertThat(token.isInvalidated()).isFalse();
        });

        assertInvalidCredentials(EMAIL, PASSWORD);
    }

    @Test
    void verificationActivatesAccountAndAllowsLogin() throws Exception {
        register();
        User user = userRepository.findByEmailIgnoreCase(EMAIL).orElseThrow();
        oneTimeTokenRepository.deleteAllInBatch();
        Instant now = Instant.now();

        GeneratedOpaqueToken verificationToken = opaqueTokenGenerator.generate();
        OneTimeToken confirmableToken = oneTimeTokenRepository.saveAndFlush(new OneTimeToken(
                user,
                TokenPurpose.VERIFY_EMAIL,
                verificationToken.hash(),
                now,
                now.plusSeconds(300)
        ));

        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody(verificationToken.value())))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User verifiedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(verifiedUser.isEmailVerified()).isTrue();
        assertThat(verifiedUser.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(oneTimeTokenRepository.findById(confirmableToken.getId()).orElseThrow().isConsumed()).isTrue();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(EMAIL, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    void invalidVerificationTokenReturnsProblemDetails() throws Exception {
        mockMvc.perform(post("/api/v1/auth/email-verification/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(tokenBody("not-a-real-verification-token")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_VERIFICATION_TOKEN"));
    }

    @Test
    void resendDoesNotRevealWhetherAnAccountExists() throws Exception {
        register();
        OneTimeToken original = oneTimeTokenRepository.findAll().getFirst();

        mockMvc.perform(post("/api/v1/auth/email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailBody(EMAIL)))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        mockMvc.perform(post("/api/v1/auth/email-verification")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailBody("unknown@example.com")))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        assertThat(oneTimeTokenRepository.findById(original.getId()).orElseThrow().isInvalidated()).isTrue();
        assertThat(oneTimeTokenRepository.findAll().stream()
                .filter(token -> !token.isConsumed() && !token.isInvalidated())
                .count()).isEqualTo(1);
    }

    @Test
    void duplicateEmailReturnsConflictAndShortPasswordReturnsValidationProblem() throws Exception {
        register();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(EMAIL.toUpperCase(), PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody("short-password@example.com", "too-short")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].pointer").value("#/password"));
    }

    private void register() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(EMAIL, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private void assertInvalidCredentials(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    private String registerBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private String loginBody(String email, String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
    }

    private String tokenBody(String token) {
        return """
                {"token":"%s"}
                """.formatted(token);
    }

    private String emailBody(String email) {
        return """
                {"email":"%s"}
                """.formatted(email);
    }
}
