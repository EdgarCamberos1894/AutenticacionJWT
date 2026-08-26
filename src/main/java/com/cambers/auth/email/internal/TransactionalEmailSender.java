package com.cambers.auth.email.internal;

public interface TransactionalEmailSender {

    EmailDeliveryReceipt send(TransactionalEmail email);
}
