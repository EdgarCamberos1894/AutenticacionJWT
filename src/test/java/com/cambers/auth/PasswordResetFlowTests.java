package com.cambers.auth;

import com.cambers.auth.entity.OneTimeToken;
import com.cambers.auth.entity.RoleName;
import com.cambers.auth.entity.SessionRevocationReason;
import com.cambers.auth.entity.TokenPurpose;
import com.cambers.auth.entity.User;
import com.cambers.auth.repository.AuthSessionRepository;
import com.cambers.auth.repository.OneTimeTokenRepository;
import com.cambers.auth.repository.RefreshTokenRepository;
import com.cambers.auth.repository.UserRepository;
import com.cambers.auth.security.token.GeneratedOpaqueToken;
import com.cambers.auth.security.token.SecureOpaqueTokenGenerator;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class PasswordResetFlowTests {

    private static final String EMAIL = "reset@example.com";
    private static final String OLD_PASSWORD = "correct horse battery staple";
    private static final String NEW_PASSWORD = "another long password for reset";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private SecureOpaqueTokenGenerator opaqueTokenGenerator;

    @BeforeEach
    void cleanDatabase() {
        oneTimeTokenRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        authSessionRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void passwordResetRequestIsGenericAndStoresOnlyAHashedOneTimeToken() throws Exception {
        createVerifiedUser();

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailBody(EMAIL)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        mockMvc.perform(post("/api/v1/auth/password-reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(emailBody("unknown@example.com")))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(oneTimeTokenRepository.findAll()).singleElement().satisfies(token -> {
            assertThat(token.getPurpose()).isEqualTo(TokenPurpose.RESET_PASSWORD);
            assertThat(token.getTokenHash()).hasSize(64);
            assertThat(token.isConsumed()).isFalse();
            assertThat(token.isInvalidated()).isFalse();
        });
    }

    @Test
    void passwordResetChangesCredentialsAndRevokesEveryExistingSession() throws Exception {
        User user = createVerifiedUser();
        MvcResult firstLogin = login(OLD_PASSWORD);
        MvcResult secondLogin = login(OLD_PASSWORD);
        String firstRefresh = JsonPath.read(firstLogin.getResponse().getContentAsString(), "$.refreshToken");
        String secondRefresh = JsonPath.read(secondLogin.getResponse().getContentAsString(), "$.refreshToken");

        GeneratedOpaqueToken resetToken = opaqueTokenGenerator.generate();
        oneTimeTokenRepository.saveAndFlush(new OneTimeToken(
                user,
                TokenPurpose.RESET_PASSWORD,
                resetToken.hash(),
                Instant.now().plusSeconds(300)
        ));

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(resetToken.value(), NEW_PASSWORD)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        User reloaded = userRepository.findById(user.getId()).orElseThrow();
        assertThat(passwordEncoder.matches(OLD_PASSWORD, reloaded.getPasswordHash())).isFalse();
        assertThat(passwordEncoder.matches(NEW_PASSWORD, reloaded.getPasswordHash())).isTrue();
        assertThat(reloaded.getPasswordHash()).startsWith("{argon2id}");

        assertThat(authSessionRepository.findAll()).hasSize(2).allSatisfy(session -> {
            assertThat(session.isRevoked()).isTrue();
            assertThat(session.getRevocationReason()).isEqualTo(SessionRevocationReason.PASSWORD_RESET);
        });
        assertThat(refreshTokenRepository.findAll()).allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
        assertThat(oneTimeTokenRepository.findAll()).allSatisfy(token -> assertThat(token.isConsumed()).isTrue());

        assertInvalidRefresh(firstRefresh);
        assertInvalidRefresh(secondRefresh);
        assertInvalidCredentials(OLD_PASSWORD);
        login(NEW_PASSWORD);
    }

    @Test
    void resetTokenIsSingleUseAndInvalidTokensUseProblemDetails() throws Exception {
        User user = createVerifiedUser();
        GeneratedOpaqueToken resetToken = opaqueTokenGenerator.generate();
        oneTimeTokenRepository.saveAndFlush(new OneTimeToken(
                user,
                TokenPurpose.RESET_PASSWORD,
                resetToken.hash(),
                Instant.now().plusSeconds(300)
        ));

        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(resetToken.value(), NEW_PASSWORD)))
                .andExpect(status().isNoContent());

        assertInvalidReset(resetToken.value());
        assertInvalidReset("not-a-real-reset-token");
    }

    @Test
    void shortReplacementPasswordReturnsSemanticValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody("some-token", "too-short")))
                .andExpect(status().isUnprocessableContent())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    private User createVerifiedUser() {
        User user = new User(EMAIL, passwordEncoder.encode(OLD_PASSWORD));
        user.verifyEmail(Instant.now());
        user.assignRole(RoleName.USER);
        return userRepository.saveAndFlush(user);
    }

    private MvcResult login(String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(password)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private void assertInvalidCredentials(String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(password)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    private void assertInvalidRefresh(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private void assertInvalidReset(String rawToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/password-reset/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(confirmBody(rawToken, NEW_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_PASSWORD_RESET_TOKEN"));
    }

    private String loginBody(String password) {
        return """
                {"email":"%s","password":"%s"}
                """.formatted(EMAIL, password);
    }

    private String emailBody(String email) {
        return """
                {"email":"%s"}
                """.formatted(email);
    }

    private String confirmBody(String token, String password) {
        return """
                {"token":"%s","newPassword":"%s"}
                """.formatted(token, password);
    }
}
