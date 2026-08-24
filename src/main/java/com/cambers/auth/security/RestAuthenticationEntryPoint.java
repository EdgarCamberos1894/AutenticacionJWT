package com.cambers.auth.security;

import com.cambers.auth.exception.ProblemCode;
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

    public RestAuthenticationEntryPoint(SecurityProblemWriter problemWriter) {
        this.problemWriter = problemWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authenticationException) throws IOException, ServletException {

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
