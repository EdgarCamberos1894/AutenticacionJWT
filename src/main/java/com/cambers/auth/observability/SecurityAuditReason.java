package com.cambers.auth.observability;

public enum SecurityAuditReason {
    NONE("none"),
    INVALID_CREDENTIALS("invalid_credentials"),
    ACCOUNT_ALREADY_EXISTS("account_already_exists"),
    INVALID_REFRESH_TOKEN("invalid_refresh_token"),
    REFRESH_TOKEN_REUSE("refresh_token_reuse"),
    INVALID_VERIFICATION_TOKEN("invalid_verification_token"),
    INVALID_PASSWORD_RESET_TOKEN("invalid_password_reset_token"),
    LOGOUT("logout"),
    LOGOUT_ALL("logout_all"),
    MANUAL_REVOCATION("manual_revocation"),
    PASSWORD_RESET("password_reset"),
    AUTHENTICATION_REQUIRED("authentication_required"),
    ACCESS_DENIED("access_denied"),
    RATE_LIMIT_EXCEEDED("rate_limit_exceeded"),
    RATE_LIMIT_BACKEND_UNAVAILABLE("rate_limit_backend_unavailable");

    private final String metricValue;

    SecurityAuditReason(String metricValue) {
        this.metricValue = metricValue;
    }

    public String metricValue() {
        return metricValue;
    }
}
