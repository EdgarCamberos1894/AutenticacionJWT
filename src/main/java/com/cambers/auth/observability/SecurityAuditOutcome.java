package com.cambers.auth.observability;

public enum SecurityAuditOutcome {
    SUCCESS("success", false),
    ACCEPTED("accepted", false),
    FAILURE("failure", true),
    DENIED("denied", true),
    DETECTED("detected", true);

    private final String metricValue;
    private final boolean warning;

    SecurityAuditOutcome(String metricValue, boolean warning) {
        this.metricValue = metricValue;
        this.warning = warning;
    }

    public String metricValue() {
        return metricValue;
    }

    public boolean isWarning() {
        return warning;
    }
}
