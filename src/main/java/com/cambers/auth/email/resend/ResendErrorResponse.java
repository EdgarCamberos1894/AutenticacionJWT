package com.cambers.auth.email.resend;

public record ResendErrorResponse(String name, String message, Integer statusCode) {
}
