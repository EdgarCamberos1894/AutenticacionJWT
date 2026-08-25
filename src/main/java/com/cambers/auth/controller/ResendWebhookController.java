package com.cambers.auth.controller;

import com.cambers.auth.email.resend.ResendWebhookService;
import com.cambers.auth.email.resend.ResendWebhookVerifier;
import com.cambers.auth.email.resend.VerifiedResendWebhook;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/resend")
public class ResendWebhookController {

    private final ResendWebhookVerifier verifier;
    private final ResendWebhookService webhookService;

    public ResendWebhookController(
            ResendWebhookVerifier verifier,
            ResendWebhookService webhookService) {
        this.verifier = verifier;
        this.webhookService = webhookService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void receive(
            @RequestBody byte[] rawBody,
            @RequestHeader HttpHeaders headers) {
        VerifiedResendWebhook verified = verifier.verify(rawBody, headers);
        webhookService.process(verified.webhookId(), rawBody);
    }
}
