package com.cambers.auth.email;

public interface TransactionalEmailSender {

    EmailDeliveryReceipt send(TransactionalEmail email);
}
