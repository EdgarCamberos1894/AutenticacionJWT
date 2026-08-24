package com.cambers.auth.exception;

import java.net.URI;

public enum ProblemCode {

    INVALID_REQUEST("INVALID_REQUEST", "invalid-request", "Invalid request"),
    VALIDATION_ERROR("VALIDATION_ERROR", "validation-error", "Request validation failed"),
    INVALID_CREDENTIALS("INVALID_CREDENTIALS", "invalid-credentials", "Invalid credentials"),
    INVALID_REFRESH_TOKEN("INVALID_REFRESH_TOKEN", "invalid-refresh-token", "Invalid refresh token"),
    INVALID_VERIFICATION_TOKEN("INVALID_VERIFICATION_TOKEN", "invalid-verification-token", "Invalid verification token"),
    INVALID_PASSWORD_RESET_TOKEN(
            "INVALID_PASSWORD_RESET_TOKEN",
            "invalid-password-reset-token",
            "Invalid password reset token"
    ),
    EMAIL_ALREADY_REGISTERED("EMAIL_ALREADY_REGISTERED", "email-already-registered", "Email already registered"),
    AUTHENTICATION_REQUIRED("AUTHENTICATION_REQUIRED", "authentication-required", "Authentication required"),
    ACCESS_DENIED("ACCESS_DENIED", "access-denied", "Access denied"),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "resource-not-found", "Resource not found"),
    CONFLICT("CONFLICT", "conflict", "Resource conflict"),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "method-not-allowed", "Method not allowed"),
    NOT_ACCEPTABLE("NOT_ACCEPTABLE", "not-acceptable", "Not acceptable"),
    UNSUPPORTED_MEDIA_TYPE("UNSUPPORTED_MEDIA_TYPE", "unsupported-media-type", "Unsupported media type"),
    RATE_LIMIT_EXCEEDED("RATE_LIMIT_EXCEEDED", "rate-limit-exceeded", "Too many requests"),
    SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE", "service-unavailable", "Service unavailable"),
    INTERNAL_ERROR("INTERNAL_ERROR", "internal-error", "Internal server error");

    private static final String TYPE_PREFIX = "urn:cambers:problem:";

    private final String value;
    private final String slug;
    private final String title;

    ProblemCode(String value, String slug, String title) {
        this.value = value;
        this.slug = slug;
        this.title = title;
    }

    public String value() {
        return value;
    }

    public URI type() {
        return URI.create(TYPE_PREFIX + slug);
    }

    public String title() {
        return title;
    }
}
