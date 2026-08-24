package com.cambers.auth.entity;

public enum SessionRevocationReason {
    LOGOUT,
    LOGOUT_ALL,
    MANUAL_REVOCATION,
    PASSWORD_RESET,
    REFRESH_TOKEN_REUSE
}
