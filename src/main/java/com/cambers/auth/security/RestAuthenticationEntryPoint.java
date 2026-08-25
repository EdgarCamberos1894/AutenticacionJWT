package com.cambers.auth.security;

import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.observability.SecurityAuditAction;
import com.cambers.auth.observability.SecurityAuditEvent;
import com.cambers.auth.observability.SecurityAuditOutcome;
import com.cambers.auth.observability.SecurityAuditPublisher;
import com.cambers.auth.observability.SecurityAuditReason;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final SecurityProblemWriter problemWriter;
    private final SecurityAuditPublisher auditPublisher;

    public RestAuthenticationEntryPoint(
            SecurityProblemWriter problemWriter,
            SecurityAuditPublisher auditPublisher) {
        this.problemWriter = problemWriter;
        this.auditPublisher = auditPublisher;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException, ServletException {

        auditPublisher.now(SecurityAuditEvent.of(
                SecurityAuditAction.AUTHORIZATION,
                SecurityAuditOutcome.DENIED,
                SecurityAuditReason.AUTHENTICATION_REQUIRED,
                null,
                null
        ));
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        problemWriter.write(
                request,
                response,
                HttpStatus.UNAUTHORIZED,
                ProblemCode.AUTHENTICATION_REQUIRED,
                "Authentication is required to access this resource."
        );
    }
}
