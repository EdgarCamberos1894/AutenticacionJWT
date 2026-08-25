package com.cambers.auth.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile({"local", "test"})
public class LoggingTransactionalEmailSender implements TransactionalEmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingTransactionalEmailSender.class);

    @Override
    public EmailDeliveryReceipt send(TransactionalEmail email) {
        log.debug(
                "Local transactional email idempotencyKey={} subject={} body={}",
                email.idempotencyKey(),
                email.subject(),
                email.text()
        );
        return new EmailDeliveryReceipt("local/" + email.idempotencyKey());
    }
}
