package com.cambers.auth;

import com.cambers.auth.account.RoleName;
import com.cambers.auth.account.internal.model.User;
import com.cambers.auth.account.internal.persistence.UserRepository;
import com.cambers.auth.authentication.internal.model.AuthSession;
import com.cambers.auth.authentication.internal.model.SessionRevocationReason;
import com.cambers.auth.authentication.internal.persistence.AuthSessionRepository;
import com.cambers.auth.authentication.internal.persistence.RefreshTokenRepository;
import com.cambers.auth.security.refresh.RefreshTokenGenerator;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest
@AutoConfigureMockMvc
class AuthenticationFlowTests {

    private static final String EMAIL = "alice@example.com";
    private static final String SECOND_EMAIL = "bob@example.com";
    private static final String PASSWORD = "correct horse battery staple";

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RefreshTokenGenerator refreshTokenGenerator;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void cleanDatabase() {
        refreshTokenRepository.deleteAllInBatch();
        authSessionRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void loginIssuesBearerTokensAndStoresOnlyTheRefreshTokenHash() throws Exception {
        User user = createUser(EMAIL);

        MvcResult result = performLogin(EMAIL, PASSWORD);

        String body = result.getResponse().getContentAsString();
        String rawRefreshToken = JsonPath.read(body, "$.refreshToken");
        String accessToken = JsonPath.read(body, "$.accessToken");
        String sessionId = JsonPath.read(body, "$.sessionId");

        assertThat(refreshTokenRepository.findAll()).singleElement().satisfies(storedToken -> {
            assertThat(storedToken.getTokenHash()).isNotEqualTo(rawRefreshToken);
            assertThat(storedToken.getTokenHash()).isEqualTo(refreshTokenGenerator.hash(rawRefreshToken));
        });

        Jwt jwt = jwtDecoder.decode(accessToken);
        assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
        assertThat(jwt.getClaimAsString("sid")).isEqualTo(sessionId);
        assertThat(jwt.getClaimAsString("token_type")).isEqualTo("access");
    }

    @Test
    void unprefixedLegacyBcryptHashAuthenticatesAndUpgradesToArgon2id() throws Exception {
        Instant now = Instant.now();
        String legacyBcryptHash = new BCryptPasswordEncoder(12).encode(PASSWORD);
        assertThat(legacyBcryptHash).startsWith("$2");

        User user = new User(EMAIL, legacyBcryptHash, now);
        user.verifyEmail(now);
        user.assignRole(RoleName.USER, now);
        userRepository.saveAndFlush(user);

        performLogin(EMAIL, PASSWORD);

        User upgradedUser = userRepository.findById(user.getId()).orElseThrow();
        assertThat(upgradedUser.getPasswordHash()).startsWith("{argon2id}");
        assertThat(passwordEncoder.matches(PASSWORD, upgradedUser.getPasswordHash())).isTrue();
    }

    @Test
    void wrongPasswordAndUnknownAccountUseTheSameAuthenticationProblem() throws Exception {
        createUser(EMAIL);

        assertInvalidCredentials(EMAIL, "definitely-the-wrong-password");
        assertInvalidCredentials("unknown@example.com", PASSWORD);
    }

    @Test
    void refreshRotationDetectsReplayAndPermanentlyRevokesTheSession() throws Exception {
        createUser(EMAIL);

        MvcResult loginResult = performLogin(EMAIL, PASSWORD);
        String loginBody = loginResult.getResponse().getContentAsString();
        String firstRefreshToken = JsonPath.read(loginBody, "$.refreshToken");
        UUID sessionId = UUID.fromString(JsonPath.read(loginBody, "$.sessionId"));

        MvcResult refreshResult = performRefresh(firstRefreshToken);
        String secondRefreshToken = JsonPath.read(
                refreshResult.getResponse().getContentAsString(),
                "$.refreshToken"
        );
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);
        assertThat(refreshTokenRepository.findAll()).hasSize(2);
        assertThat(refreshTokenRepository.findAll().stream().filter(token -> token.isUsed()).count()).isEqualTo(1);

        assertInvalidRefresh(firstRefreshToken);

        AuthSession revokedSession = authSessionRepository.findById(sessionId).orElseThrow();
        assertThat(revokedSession.isRevoked()).isTrue();
        assertThat(revokedSession.getRevocationReason()).isEqualTo(SessionRevocationReason.REFRESH_TOKEN_REUSE);
        assertThat(refreshTokenRepository.findAll()).allSatisfy(token -> assertThat(token.isRevoked()).isTrue());

        assertInvalidRefresh(secondRefreshToken);
    }

    @Test
    void logoutReturnsNoContentAndPreventsFurtherRefresh() throws Exception {
        createUser(EMAIL);
        MvcResult loginResult = performLogin(EMAIL, PASSWORD);
        String body = loginResult.getResponse().getContentAsString();
        String accessToken = JsonPath.read(body, "$.accessToken");
        String refreshToken = JsonPath.read(body, "$.refreshToken");
        UUID sessionId = UUID.fromString(JsonPath.read(body, "$.sessionId"));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        assertThat(authSessionRepository.findById(sessionId).orElseThrow().isRevoked()).isTrue();
        assertInvalidRefresh(refreshToken);

        mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void sessionListMarksCurrentAndAllowsRevokingAnotherOwnedSession() throws Exception {
        createUser(EMAIL);
        MvcResult firstLogin = performLogin(EMAIL, PASSWORD);
        MvcResult secondLogin = performLogin(EMAIL, PASSWORD);

        String firstBody = firstLogin.getResponse().getContentAsString();
        String secondBody = secondLogin.getResponse().getContentAsString();
        String firstAccessToken = JsonPath.read(firstBody, "$.accessToken");
        UUID firstSessionId = UUID.fromString(JsonPath.read(firstBody, "$.sessionId"));
        UUID secondSessionId = UUID.fromString(JsonPath.read(secondBody, "$.sessionId"));
        String secondRefreshToken = JsonPath.read(secondBody, "$.refreshToken");

        MvcResult sessionsResult = mockMvc.perform(get("/api/v1/auth/sessions")
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstAccessToken)))
                .andExpect(status().isOk())
                .andReturn();

        List<Map<String, Object>> sessions = JsonPath.read(sessionsResult.getResponse().getContentAsString(), "$");
        assertThat(sessions).hasSize(2);
        assertThat(sessions).anySatisfy(session -> {
            assertThat(session.get("sessionId")).isEqualTo(firstSessionId.toString());
            assertThat(session.get("current")).isEqualTo(true);
        });

        mockMvc.perform(delete("/api/v1/auth/sessions/{sessionId}", secondSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstAccessToken)))
                .andExpect(status().isNoContent());

        assertInvalidRefresh(secondRefreshToken);
        assertThat(authSessionRepository.findById(secondSessionId).orElseThrow().isRevoked()).isTrue();
    }

    @Test
    void aUserCannotRevokeAnotherUsersSession() throws Exception {
        createUser(EMAIL);
        createUser(SECOND_EMAIL);

        MvcResult firstLogin = performLogin(EMAIL, PASSWORD);
        MvcResult secondLogin = performLogin(SECOND_EMAIL, PASSWORD);
        String firstAccessToken = JsonPath.read(firstLogin.getResponse().getContentAsString(), "$.accessToken");
        UUID secondSessionId = UUID.fromString(JsonPath.read(secondLogin.getResponse().getContentAsString(), "$.sessionId"));

        mockMvc.perform(delete("/api/v1/auth/sessions/{sessionId}", secondSessionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(firstAccessToken)))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        assertThat(authSessionRepository.findById(secondSessionId).orElseThrow().isRevoked()).isFalse();
    }

    @Test
    void logoutAllRevokesEverySessionForTheAuthenticatedUser() throws Exception {
        createUser(EMAIL);
        MvcResult firstLogin = performLogin(EMAIL, PASSWORD);
        MvcResult secondLogin = performLogin(EMAIL, PASSWORD);

        String firstBody = firstLogin.getResponse().getContentAsString();
        String secondBody = secondLogin.getResponse().getContentAsString();
        String accessToken = JsonPath.read(firstBody, "$.accessToken");
        String firstRefreshToken = JsonPath.read(firstBody, "$.refreshToken");
        String secondRefreshToken = JsonPath.read(secondBody, "$.refreshToken");

        mockMvc.perform(post("/api/v1/auth/logout-all")
                        .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
                .andExpect(status().isNoContent());

        assertThat(authSessionRepository.findAll()).allSatisfy(session -> assertThat(session.isRevoked()).isTrue());
        assertThat(refreshTokenRepository.findAll()).allSatisfy(token -> assertThat(token.isRevoked()).isTrue());
        assertInvalidRefresh(firstRefreshToken);
        assertInvalidRefresh(secondRefreshToken);
    }

    @Test
    void publicAuthResourceStillReturnsMethodNotAllowedForUnsupportedMethods() throws Exception {
        mockMvc.perform(get("/api/v1/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    private User createUser(String email) {
        Instant now = Instant.now();
        User user = new User(email, passwordEncoder.encode(PASSWORD), now);
        user.verifyEmail(now);
        user.assignRole(RoleName.USER, now);
        return userRepository.saveAndFlush(user);
    }

    private MvcResult performLogin(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.USER_AGENT, "AuthenticationFlowTests/1.0")
                        .content(loginBody(email, password)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(header().string(HttpHeaders.PRAGMA, "no-cache"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.sessionId").isNotEmpty())
                .andReturn();
    }

    private MvcResult performRefresh(String refreshToken) throws Exception {
        return mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();
    }

    private void assertInvalidCredentials(String email, String password) throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginBody(email, password)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.detail").value("The supplied credentials are invalid."));
    }

    private void assertInvalidRefresh(String refreshToken) throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshBody(refreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    private String bearer(String accessToken) { return "Bearer " + accessToken; }
    private String loginBody(String email, String password) { return """
            {"email":"%s","password":"%s"}
            """.formatted(email, password); }
    private String refreshBody(String refreshToken) { return """
            {"refreshToken":"%s"}
            """.formatted(refreshToken); }
}
