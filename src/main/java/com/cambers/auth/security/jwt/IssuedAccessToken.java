package com.cambers.auth.security.jwt;

import java.time.Instant;

public record IssuedAccessToken(String value, Instant expiresAt) {
}
