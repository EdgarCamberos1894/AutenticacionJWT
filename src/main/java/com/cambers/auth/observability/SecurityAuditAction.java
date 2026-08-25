package com.cambers.auth.observability;

public enum SecurityAuditAction {
    REGISTRATION("auth.registration", "registration"),
    LOGIN("auth.login", "login"),
    REFRESH("auth.refresh", "refresh"),
    EMAIL_VERIFICATION_REQUEST("auth.email_verification.request", "email_verification_request"),
    EMAIL_VERIFICATION_CONFIRM("auth.email_verification.confirm", "email_verification_confirm"),
    PASSWORD_RESET_REQUEST("auth.password_reset.request", "password_reset_request"),
    PASSWORD_RESET_CONFIRM("auth.password_reset.confirm", "password_reset_confirm"),
    SESSION_REVOCATION("auth.session.revocation", "session_revocation"),
    AUTHORIZATION("auth.authorization", "authorization"),
    RATE_LIMIT("auth.rate_limit", "rate_limit");

    private final String eventName;
    private final String metricValue;

    SecurityAuditAction(String eventName, String metricValue) {
        this.eventName = eventName;
        this.metricValue = metricValue;
    }

    public String eventName() {
        return eventName;
    }

    public String metricValue() {
        return metricValue;
    }
}
