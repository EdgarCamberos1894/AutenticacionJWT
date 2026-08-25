package com.cambers.auth.observability;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class SecurityAuditEventListener {

    private final SecurityAuditRecorder recorder;

    public SecurityAuditEventListener(SecurityAuditRecorder recorder) {
        this.recorder = recorder;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSecurityAuditEvent(SecurityAuditEvent event) {
        recorder.record(event);
    }
}
