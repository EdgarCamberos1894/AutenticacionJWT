package com.cambers.auth.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditRecorder {

    static final String METRIC_NAME = "auth.security.events";
    private static final Logger auditLog = LoggerFactory.getLogger("security.audit");
    private static final Logger log = LoggerFactory.getLogger(SecurityAuditRecorder.class);

    private final MeterRegistry meterRegistry;
    private final Tracer tracer;

    public SecurityAuditRecorder(MeterRegistry meterRegistry, Tracer tracer) {
        this.meterRegistry = meterRegistry;
        this.tracer = tracer;
    }

    public void record(SecurityAuditEvent event) {
        recordMetricBestEffort(event);
        enrichCurrentSpanBestEffort(event);
        writeStructuredLog(event);
    }

    private void recordMetricBestEffort(SecurityAuditEvent event) {
        try {
            Counter.builder(METRIC_NAME)
                    .description("Security-relevant authentication events")
                    .tag("action", event.action().metricValue())
                    .tag("outcome", event.outcome().metricValue())
                    .tag("reason", event.reason().metricValue())
                    .register(meterRegistry)
                    .increment();
        } catch (RuntimeException exception) {
            log.error("Could not record security audit metric action={} outcome={}",
                    event.action().metricValue(), event.outcome().metricValue(), exception);
        }
    }

    private void enrichCurrentSpanBestEffort(SecurityAuditEvent event) {
        try {
            Span span = tracer.currentSpan();
            if (span == null) {
                return;
            }
            span.event(event.action().eventName());
            span.tag("auth.action", event.action().metricValue());
            span.tag("auth.outcome", event.outcome().metricValue());
            span.tag("auth.reason", event.reason().metricValue());
        } catch (RuntimeException exception) {
            log.error("Could not enrich trace with security audit event action={} outcome={}",
                    event.action().metricValue(), event.outcome().metricValue(), exception);
        }
    }

    private void writeStructuredLog(SecurityAuditEvent event) {
        LoggingEventBuilder builder = event.outcome().isWarning()
                ? auditLog.atWarn()
                : auditLog.atInfo();

        builder
                .addKeyValue("event.name", event.action().eventName())
                .addKeyValue("event.category", "authentication")
                .addKeyValue("auth.action", event.action().metricValue())
                .addKeyValue("auth.outcome", event.outcome().metricValue())
                .addKeyValue("auth.reason", event.reason().metricValue());

        if (event.userId() != null) {
            builder.addKeyValue("user.id", event.userId().toString());
        }
        if (event.sessionId() != null) {
            builder.addKeyValue("session.id", event.sessionId().toString());
        }

        builder.log("Security audit event");
    }
}
