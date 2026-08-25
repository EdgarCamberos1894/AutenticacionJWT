package com.cambers.auth.account.internal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Map;

@Configuration(proxyBeanMethods = false)
public class PasswordEncodingConfig {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    @Bean
    PasswordEncoder passwordEncoder() {
        PasswordEncoder argon2id = new Argon2PasswordEncoder(
                SALT_LENGTH,
                HASH_LENGTH,
                PARALLELISM,
                MEMORY_KIB,
                ITERATIONS
        );
        PasswordEncoder bcrypt = new BCryptPasswordEncoder(12);

        DelegatingPasswordEncoder delegatingPasswordEncoder = new DelegatingPasswordEncoder(
                "argon2id",
                Map.of(
                        "argon2id", argon2id,
                        "bcrypt", bcrypt
                )
        );
        // The legacy project persisted BCrypt hashes without a {bcrypt} prefix.
        // Use BCrypt only as the fallback matcher; every new encode still uses Argon2id.
        delegatingPasswordEncoder.setDefaultPasswordEncoderForMatches(bcrypt);
        return delegatingPasswordEncoder;
    }
}
