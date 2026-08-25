package com.cambers.auth;

import com.cambers.auth.account.internal.application.EmailVerificationService;
import com.cambers.auth.account.internal.application.RegistrationService;
import com.cambers.auth.dto.RegisterRequest;
import com.cambers.auth.entity.OneTimeToken;
import com.cambers.auth.repository.AuthSessionRepository;
import com.cambers.auth.repository.OneTimeTokenRepository;
import com.cambers.auth.repository.RefreshTokenRepository;
import com.cambers.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(properties = "security.rate-limit.enabled=false")
class OneTimeTokenConcurrencyTests {

    private static final String EMAIL = "concurrent-verification@example.com";
    private static final String PASSWORD = "correct horse battery staple";

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Autowired
    private RegistrationService registrationService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private OneTimeTokenRepository oneTimeTokenRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private AuthSessionRepository authSessionRepository;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void cleanDatabase() {
        oneTimeTokenRepository.deleteAllInBatch();
        refreshTokenRepository.deleteAllInBatch();
        authSessionRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    void concurrentVerificationResendsLeaveExactlyOneActiveToken() throws Exception {
        registrationService.register(new RegisterRequest(EMAIL, PASSWORD));

        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<?> first = executor.submit(() -> resendWhenReleased(ready, start));
            Future<?> second = executor.submit(() -> resendWhenReleased(ready, start));

            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
        }

        List<OneTimeToken> tokens = oneTimeTokenRepository.findAll();
        assertThat(tokens).hasSize(3);
        assertThat(tokens.stream().filter(token -> !token.isConsumed() && !token.isInvalidated()))
                .hasSize(1);
        assertThat(tokens.stream().filter(OneTimeToken::isInvalidated))
                .hasSize(2);
    }

    private void resendWhenReleased(CountDownLatch ready, CountDownLatch start) {
        ready.countDown();
        try {
            if (!start.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent verification test did not start in time");
            }
            emailVerificationService.resend(EMAIL);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent verification test was interrupted", exception);
        }
    }
}
