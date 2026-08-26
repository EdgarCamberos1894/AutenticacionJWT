package com.cambers.auth.account.internal.password;

import com.cambers.auth.platform.ApiException;
import com.cambers.auth.platform.ProblemCode;
import com.cambers.auth.platform.RetryAfterProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Component
public class PasswordCompromisePolicy {

    private final CompromisedPasswordChecker checker;

    public PasswordCompromisePolicy(CompromisedPasswordChecker checker) {
        this.checker = checker;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void requireSafe(String password) {
        CompromisedPasswordDecision decision;
        try {
            decision = Objects.requireNonNull(
                    checker.check(password),
                    "CompromisedPasswordChecker returned null"
            );
        } catch (RuntimeException exception) {
            throw new PasswordSafetyServiceUnavailableException(exception);
        }

        if (decision.isCompromised()) {
            throw new CompromisedPasswordRejectedException();
        }
    }

    private static final class CompromisedPasswordRejectedException extends ApiException {

        private CompromisedPasswordRejectedException() {
            super(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    ProblemCode.COMPROMISED_PASSWORD,
                    "The password has appeared in known data breaches and cannot be used."
            );
        }
    }

    private static final class PasswordSafetyServiceUnavailableException
            extends ApiException implements RetryAfterProvider {

        private PasswordSafetyServiceUnavailableException(Throwable cause) {
            super(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemCode.SERVICE_UNAVAILABLE,
                    "Password safety validation is temporarily unavailable."
            );
            initCause(cause);
        }

        @Override
        public long retryAfterSeconds() {
            return 5;
        }
    }
}
