package com.cambers.auth.observability;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditPublisher {

    private final ApplicationEventPublisher applicationEventPublisher;
    private final SecurityAuditRecorder recorder;

    public SecurityAuditPublisher(
            ApplicationEventPublisher applicationEventPublisher,
            SecurityAuditRecorder recorder) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.recorder = recorder;
    }

    public void afterCommit(SecurityAuditEvent event) {
        applicationEventPublisher.publishEvent(event);
    }

    public void now(SecurityAuditEvent event) {
        recorder.record(event);
    }
}
