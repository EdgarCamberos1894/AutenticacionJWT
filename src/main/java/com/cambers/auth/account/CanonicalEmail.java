package com.cambers.auth.account;

import java.util.Locale;
import java.util.Objects;

public record CanonicalEmail(String value) {

    public CanonicalEmail {
        Objects.requireNonNull(value, "value must not be null");
    }

    public static CanonicalEmail from(String email) {
        Objects.requireNonNull(email, "email must not be null");
        return new CanonicalEmail(email.strip().toLowerCase(Locale.ROOT));
    }
}
