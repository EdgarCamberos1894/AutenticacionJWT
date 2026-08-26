package com.cambers.auth.email.internal;

public class EmailDeliveryException extends RuntimeException {

    private final boolean retryable;
    private final Integer providerStatus;
    private final String providerCode;

    public EmailDeliveryException(
            String message,
            boolean retryable,
            Integer providerStatus,
            String providerCode,
            Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.providerStatus = providerStatus;
        this.providerCode = providerCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public Integer getProviderStatus() {
        return providerStatus;
    }

    public String getProviderCode() {
        return providerCode;
    }
}
