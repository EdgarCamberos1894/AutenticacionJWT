package com.cambers.auth;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Testcontainers
@SpringBootTest(properties = {
        "security.rate-limit.enabled=true",
        "security.rate-limit.login.limit=2",
        "security.rate-limit.login.window=PT5M",
        "security.rate-limit.login-account.limit=2",
        "security.rate-limit.login-account.window=PT5M",
        "management.health.redis.enabled=true"
})
@AutoConfigureMockMvc
class RateLimitIntegrationTests {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:18.6-alpine");

    @Container
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:8.10.1-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add(
                "spring.data.redis.url",
                () -> "redis://" + redis.getHost() + ":" + redis.getMappedPort(6379)
        );
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void clientLoginLimitIsSharedInRedisAndReturnsStandardsBased429() throws Exception {
        String request = loginBody("client-limit@example.com");
        double before = rateLimitDeniedCount();

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(request))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        }

        var result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER))
                .andReturn();

        long retryAfter = Long.parseLong(result.getResponse().getHeader(HttpHeaders.RETRY_AFTER));
        assertThat(retryAfter).isPositive().isLessThanOrEqualTo(300);
        assertThat(rateLimitDeniedCount()).isEqualTo(before + 1.0);
    }

    @Test
    void accountLoginLimitCannotBeBypassedByChangingClientIp() throws Exception {
        String request = loginBody("account-limit@example.com");

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(mockRequest -> {
                            mockRequest.setRemoteAddr("198.51.100.11");
                            return mockRequest;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(mockRequest -> {
                            mockRequest.setRemoteAddr("198.51.100.12");
                            return mockRequest;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/login")
                        .with(mockRequest -> {
                            mockRequest.setRemoteAddr("198.51.100.13");
                            return mockRequest;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isTooManyRequests())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"))
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    private double rateLimitDeniedCount() {
        var counter = meterRegistry.find("auth.security.events")
                .tags(
                        "action", "rate_limit",
                        "outcome", "denied",
                        "reason", "rate_limit_exceeded"
                )
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private String loginBody(String email) {
        return """
                {"email":"%s","password":"correct horse battery staple"}
                """.formatted(email);
    }
}
