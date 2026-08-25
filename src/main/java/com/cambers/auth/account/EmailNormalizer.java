package com.cambers.auth.account;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

@Component
public class EmailNormalizer {

    public String normalize(String email) {
        Objects.requireNonNull(email, "email must not be null");
        return email.strip().toLowerCase(Locale.ROOT);
    }
}
