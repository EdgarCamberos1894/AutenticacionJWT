package com.cambers.auth.email;

public record EmailTag(String name, String value) {
    public EmailTag {
        if (name == null || !name.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new IllegalArgumentException("Email tag name must contain only ASCII letters, digits, underscores or dashes");
        }
        if (value == null || !value.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new IllegalArgumentException("Email tag value must contain only ASCII letters, digits, underscores or dashes");
        }
    }
}
