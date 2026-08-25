package com.cambers.auth.authentication.internal.token;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {
}
