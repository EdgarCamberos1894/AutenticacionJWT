package com.cambers.auth.email.resend;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Provider callback adapter owned by the delivery module.
 *
 * <p>Keeping this controller next to the Resend verifier and handler prevents
 * external web adapters from reaching into provider-specific module internals.</p>
 */
@RestController
@RequestMapping("/api/v1/webhooks/resend")
class ResendWebhookController {

    private final ResendWebhookVerifier verifier;
    private final ResendWebhookService webhookService;

    ResendWebhookController(
            ResendWebhookVerifier verifier,
            ResendWebhookService webhookService) {
        this.verifier = verifier;
        this.webhookService = webhookService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void receive(
            @RequestBody byte[] rawBody,
            @RequestHeader HttpHeaders headers) {
        VerifiedResendWebhook verified = verifier.verify(rawBody, headers);
        webhookService.process(verified.webhookId(), rawBody);
    }
}
