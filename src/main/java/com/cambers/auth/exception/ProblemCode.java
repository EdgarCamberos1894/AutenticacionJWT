package com.cambers.auth.exception;

import java.net.URI;

public enum ProblemCode {

    INVALID_REQUEST("invalid-request", "Invalid request"),
    VALIDATION_ERROR("validation-error", "Request validation failed"),
    INVALID_CREDENTIALS("invalid-credentials", "Invalid credentials"),
    INVALID_REFRESH_TOKEN("invalid-refresh-token", "Invalid refresh token"),
    AUTHENTICATION_REQUIRED("authentication-required", "Authentication required"),
    ACCESS_DENIED("access-denied", "Access denied"),
    RESOURCE_NOT_FOUND("resource-not-found", "Resource not found"),
    CONFLICT("conflict", "Resource conflict"),
    METHOD_NOT_ALLOWED("method-not-allowed", "Method not allowed"),
    UNSUPPORTED_MEDIA_TYPE("unsupported-media-type", "Unsupported media type"),
    RATE_LIMIT_EXCEEDED("rate-limit-exceeded", "Too many requests"),
    INTERNAL_ERROR("internal-error", "Internal server error");

    private static final String TYPE_PREFIX = "urn:cambers:problem:";

    private final String slug;
    private final String title;

    ProblemCode(String slug, String title) {
        this.slug = slug;
        this.title = title;
    }

    public URI type() {
        return URI.create(TYPE_PREFIX + slug);
    }

    public String title() {
        return title;
    }
}
