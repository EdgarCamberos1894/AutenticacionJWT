package com.cambers.auth.platform;

/**
 * Exposes retry metadata to the HTTP problem renderer without coupling the platform module
 * to a concrete infrastructure capability such as Redis-backed rate limiting.
 */
public interface RetryAfterProvider {

    long retryAfterSeconds();
}
