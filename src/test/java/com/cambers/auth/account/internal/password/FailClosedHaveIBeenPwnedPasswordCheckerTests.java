package com.cambers.auth.account.internal.password;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class FailClosedHaveIBeenPwnedPasswordCheckerTests {

    private static final String BASE_URL = "https://api.pwnedpasswords.test/range/";
    private static final String PASSWORD = "correct horse battery staple";

    private MockRestServiceServer server;
    private FailClosedHaveIBeenPwnedPasswordChecker checker;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        checker = new FailClosedHaveIBeenPwnedPasswordChecker(builder.build());
    }

    @Test
    void queriesOnlyFiveCharacterHashPrefixAndDetectsCompromisedPassword() throws Exception {
        String hash = sha1Hex(PASSWORD);
        String prefix = hash.substring(0, 5);
        String suffix = hash.substring(5);

        server.expect(requestTo(BASE_URL + prefix))
                .andRespond(withSuccess("00000000000000000000000000000000000:1\r\n" + suffix + ":42\r\n", MediaType.TEXT_PLAIN));

        CompromisedPasswordDecision decision = checker.check(PASSWORD);

        assertThat(decision.isCompromised()).isTrue();
        server.verify();
    }

    @Test
    void returnsCleanWhenRangeDoesNotContainFullHashSuffix() throws Exception {
        String hash = sha1Hex(PASSWORD);
        String prefix = hash.substring(0, 5);

        server.expect(requestTo(BASE_URL + prefix))
                .andRespond(withSuccess("00000000000000000000000000000000000:1\r\n", MediaType.TEXT_PLAIN));

        assertThat(checker.check(PASSWORD).isCompromised()).isFalse();
        server.verify();
    }

    @Test
    void propagatesProviderFailureInsteadOfTreatingPasswordAsClean() throws Exception {
        String prefix = sha1Hex(PASSWORD).substring(0, 5);

        server.expect(requestTo(BASE_URL + prefix))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> checker.check(PASSWORD)).isInstanceOf(RuntimeException.class);
        server.verify();
    }

    @Test
    void rejectsUnexpectedEmptyProviderResponse() throws Exception {
        String prefix = sha1Hex(PASSWORD).substring(0, 5);

        server.expect(requestTo(BASE_URL + prefix))
                .andRespond(withSuccess("", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> checker.check(PASSWORD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty password-range response");
        server.verify();
    }

    private String sha1Hex(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        return HexFormat.of()
                .formatHex(digest.digest(password.getBytes(StandardCharsets.UTF_8)))
                .toUpperCase(Locale.ROOT);
    }
}
