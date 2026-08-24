package com.cambers.auth.email.resend;

import com.cambers.auth.config.properties.ResendProperties;
import com.cambers.auth.email.EmailDeliveryException;
import com.cambers.auth.email.EmailTag;
import com.cambers.auth.email.TransactionalEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ResendTransactionalEmailSenderTests {

    private static final String API_KEY = "re_test_secret";
    private static final String IDEMPOTENCY_KEY = "auth/email-verification/4f236fbe-3bc2-42d9-9495-c7d6519f5977";

    private ResendProperties properties;
    private MockRestServiceServer server;
    private ResendTransactionalEmailSender sender;

    @BeforeEach
    void setUp() {
        properties = new ResendProperties(
                API_KEY,
                URI.create("https://api.resend.test"),
                "Authentication",
                "no-reply@example.com",
                "support@example.com",
                Duration.ofSeconds(2),
                Duration.ofSeconds(5)
        );

        RestClient.Builder builder = ResendTransactionalEmailSender.configure(RestClient.builder(), properties);
        server = MockRestServiceServer.bindTo(builder).build();
        sender = new ResendTransactionalEmailSender(builder.build(), properties);
    }

    @Test
    void sendsIdempotentTransactionalJsonPayloadWithExpectedHeaders() {
        server.expect(requestTo("https://api.resend.test/emails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + API_KEY))
                .andExpect(header("Idempotency-Key", IDEMPOTENCY_KEY))
                .andExpect(header(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("""
                        {
                          "from":"Authentication <no-reply@example.com>",
                          "to":["person@example.com"],
                          "subject":"Verify your email address",
                          "html":"<p>Verify</p>",
                          "text":"Verify",
                          "reply_to":["support@example.com"],
                          "tags":[{"name":"category","value":"email_verification"}]
                        }
                        """))
                .andRespond(withSuccess("{\"id\":\"email_123\"}", MediaType.APPLICATION_JSON));

        var receipt = sender.send(email());

        assertThat(receipt.providerMessageId()).isEqualTo("email_123");
        server.verify();
    }

    @Test
    void classifiesRateLimitAsRetryableWithoutSurfacingProviderMessage() {
        server.expect(requestTo("https://api.resend.test/emails"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "name":"rate_limit_exceeded",
                                  "message":"Too many requests for person@example.com",
                                  "statusCode":429
                                }
                                """));

        assertThatThrownBy(() -> sender.send(email()))
                .isInstanceOfSatisfying(EmailDeliveryException.class, exception -> {
                    assertThat(exception.isRetryable()).isTrue();
                    assertThat(exception.getProviderStatus()).isEqualTo(429);
                    assertThat(exception.getProviderCode()).isEqualTo("rate_limit_exceeded");
                    assertThat(exception.getMessage()).doesNotContain("person@example.com");
                });

        server.verify();
    }

    @Test
    void classifiesInvalidIdempotentPayloadAsPermanentFailure() {
        server.expect(requestTo("https://api.resend.test/emails"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "name":"invalid_idempotent_request",
                                  "message":"The key was already used with a different payload",
                                  "statusCode":409
                                }
                                """));

        assertThatThrownBy(() -> sender.send(email()))
                .isInstanceOfSatisfying(EmailDeliveryException.class, exception -> {
                    assertThat(exception.isRetryable()).isFalse();
                    assertThat(exception.getProviderStatus()).isEqualTo(409);
                    assertThat(exception.getProviderCode()).isEqualTo("invalid_idempotent_request");
                });

        server.verify();
    }

    private TransactionalEmail email() {
        return new TransactionalEmail(
                "person@example.com",
                "Verify your email address",
                "<p>Verify</p>",
                "Verify",
                IDEMPOTENCY_KEY,
                List.of(new EmailTag("category", "email_verification"))
        );
    }
}
