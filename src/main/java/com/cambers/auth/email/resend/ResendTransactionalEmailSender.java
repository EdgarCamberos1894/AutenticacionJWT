package com.cambers.auth.email.resend;

import com.cambers.auth.email.internal.EmailDeliveryException;
import com.cambers.auth.email.internal.EmailDeliveryReceipt;
import com.cambers.auth.email.internal.EmailTag;
import com.cambers.auth.email.internal.TransactionalEmail;
import com.cambers.auth.email.internal.TransactionalEmailSender;
import com.cambers.auth.email.internal.config.ResendProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Profile("prod")
public class ResendTransactionalEmailSender implements TransactionalEmailSender {

    private static final Logger log = LoggerFactory.getLogger(ResendTransactionalEmailSender.class);
    private static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final RestClient restClient;
    private final ResendProperties properties;

    public ResendTransactionalEmailSender(ResendProperties properties) {
        this(configure(RestClient.builder(), properties).build(), properties);
    }

    ResendTransactionalEmailSender(RestClient restClient, ResendProperties properties) {
        this.restClient = restClient;
        this.properties = properties;
    }

    static RestClient.Builder configure(RestClient.Builder builder, ResendProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(requestFactory)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.USER_AGENT, "authentication-service/resend");
    }

    @Override
    public EmailDeliveryReceipt send(TransactionalEmail email) {
        try {
            EmailDeliveryReceipt receipt = restClient.post()
                    .uri("/emails")
                    .header(IDEMPOTENCY_HEADER, email.idempotencyKey())
                    .body(requestBody(email))
                    .exchange((request, response) -> {
                        int status = response.getStatusCode().value();
                        if (response.getStatusCode().is2xxSuccessful()) {
                            ResendSendEmailResponse body = response.bodyTo(ResendSendEmailResponse.class);
                            if (body == null || body.id() == null || body.id().isBlank()) {
                                throw new EmailDeliveryException(
                                        "Resend returned a successful response without an email id",
                                        false,
                                        status,
                                        null,
                                        null
                                );
                            }
                            return new EmailDeliveryReceipt(body.id());
                        }

                        ResendErrorResponse error = readError(response, status);
                        String providerCode = error == null ? null : error.name();
                        throw new EmailDeliveryException(
                                "Resend rejected the transactional email request",
                                isRetryable(status, providerCode),
                                status,
                                providerCode,
                                null
                        );
                    });

            log.info("Resend accepted transactional email providerMessageId={}", receipt.providerMessageId());
            return receipt;
        } catch (EmailDeliveryException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new EmailDeliveryException(
                    "Could not reach Resend",
                    true,
                    null,
                    null,
                    exception
            );
        } catch (RestClientException exception) {
            throw new EmailDeliveryException(
                    "Could not process the Resend API response",
                    false,
                    null,
                    null,
                    exception
            );
        }
    }

    private Map<String, Object> requestBody(TransactionalEmail email) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("from", properties.from());
        body.put("to", List.of(email.recipient()));
        body.put("subject", email.subject());
        body.put("html", email.html());
        body.put("text", email.text());
        if (properties.hasReplyTo()) {
            body.put("reply_to", List.of(properties.replyTo()));
        }
        body.put("tags", email.tags().stream().map(this::tagBody).toList());
        return body;
    }

    private Map<String, String> tagBody(EmailTag tag) {
        return Map.of("name", tag.name(), "value", tag.value());
    }

    private ResendErrorResponse readError(
            RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse response,
            int status) {
        try {
            return response.bodyTo(ResendErrorResponse.class);
        } catch (RestClientException exception) {
            log.warn("Could not decode Resend error response status={}", status);
            return null;
        }
    }

    private boolean isRetryable(int status, String providerCode) {
        return status == 429
                || status >= 500
                || (status == 409 && "concurrent_idempotent_requests".equals(providerCode));
    }
}
