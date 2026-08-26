package com.cambers.auth.platform.internal.security;

import com.cambers.auth.observability.SecurityAuditAction;
import com.cambers.auth.observability.SecurityAuditEvent;
import com.cambers.auth.observability.SecurityAuditOutcome;
import com.cambers.auth.observability.SecurityAuditPublisher;
import com.cambers.auth.observability.SecurityAuditReason;
import com.cambers.auth.platform.ProblemCode;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    private final SecurityProblemWriter problemWriter;
    private final SecurityAuditPublisher auditPublisher;

    public RestAccessDeniedHandler(
            SecurityProblemWriter problemWriter,
            SecurityAuditPublisher auditPublisher) {
        this.problemWriter = problemWriter;
        this.auditPublisher = auditPublisher;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {

        auditPublisher.now(SecurityAuditEvent.of(
                SecurityAuditAction.AUTHORIZATION,
                SecurityAuditOutcome.DENIED,
                SecurityAuditReason.ACCESS_DENIED,
                null,
                null
        ));
        problemWriter.write(
                request,
                response,
                HttpStatus.FORBIDDEN,
                ProblemCode.ACCESS_DENIED,
                "You do not have permission to access this resource."
        );
    }
}
