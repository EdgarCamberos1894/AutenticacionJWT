package com.cambers.auth.service;

import com.cambers.auth.account.CanonicalEmail;
import org.springframework.stereotype.Component;

@Component
public class EmailNormalizer {

    public String normalize(String email) {
        return CanonicalEmail.from(email).value();
    }
}
