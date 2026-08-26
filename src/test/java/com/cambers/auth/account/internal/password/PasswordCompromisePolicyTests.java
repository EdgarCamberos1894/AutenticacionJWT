package com.cambers.auth.account.internal.password;

import com.cambers.auth.platform.ApiException;
import com.cambers.auth.platform.ProblemCode;
import com.cambers.auth.platform.RetryAfterProvider;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.password.CompromisedPasswordChecker;
import org.springframework.security.authentication.password.CompromisedPasswordDecision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PasswordCompromisePolicyTests {

    @Test
    void allowsPasswordThatCheckerReportsAsClean() {
        PasswordCompromisePolicy policy = policy(false);

        assertThatCode(() -> policy.requireSafe("a sufficiently long password"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsKnownCompromisedPasswordWithStable422ProblemCode() {
        PasswordCompromisePolicy policy = policy(true);

        assertThatThrownBy(() -> policy.requireSafe("known breached password"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
                    assertThat(exception.code()).isEqualTo(ProblemCode.COMPROMISED_PASSWORD);
                    assertThat(exception.getMessage()).doesNotContain("known breached password");
                });
    }

    @Test
    void failsClosedWhenPasswordSafetyProviderIsUnavailable() {
        CompromisedPasswordChecker failingChecker = password -> {
            throw new IllegalStateException("provider unavailable");
        };
        PasswordCompromisePolicy policy = new PasswordCompromisePolicy(failingChecker);

        assertThatThrownBy(() -> policy.requireSafe("a sufficiently long password"))
                .isInstanceOfSatisfying(ApiException.class, exception -> {
                    assertThat(exception.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(exception.code()).isEqualTo(ProblemCode.SERVICE_UNAVAILABLE);
                    assertThat(exception).isInstanceOf(RetryAfterProvider.class);
                    assertThat(((RetryAfterProvider) exception).retryAfterSeconds()).isEqualTo(5);
                });
    }

    private PasswordCompromisePolicy policy(boolean compromised) {
        CompromisedPasswordChecker checker = password -> new CompromisedPasswordDecision(compromised);
        return new PasswordCompromisePolicy(checker);
    }
}
