package com.cambers.auth.observability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SecurityAuditRecorderTests {

    @Test
    void recordsOnlyBoundedMetricTagsAndEnrichesCurrentSpan() {
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        Tracer tracer = mock(Tracer.class);
        Span span = mock(Span.class);
        when(tracer.currentSpan()).thenReturn(span);
        SecurityAuditRecorder recorder = new SecurityAuditRecorder(meterRegistry, tracer);

        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        recorder.record(SecurityAuditEvent.of(
                SecurityAuditAction.LOGIN,
                SecurityAuditOutcome.SUCCESS,
                SecurityAuditReason.NONE,
                userId,
                sessionId
        ));

        assertThat(meterRegistry.get(SecurityAuditRecorder.METRIC_NAME)
                .tags(
                        "action", "login",
                        "outcome", "success",
                        "reason", "none"
                )
                .counter()
                .count()).isEqualTo(1.0);
        assertThat(meterRegistry.find(SecurityAuditRecorder.METRIC_NAME)
                .tag("user.id", userId.toString())
                .counter()).isNull();
        assertThat(meterRegistry.find(SecurityAuditRecorder.METRIC_NAME)
                .tag("session.id", sessionId.toString())
                .counter()).isNull();

        verify(span).event("auth.login");
        verify(span).tag("auth.action", "login");
        verify(span).tag("auth.outcome", "success");
        verify(span).tag("auth.reason", "none");
    }
}
