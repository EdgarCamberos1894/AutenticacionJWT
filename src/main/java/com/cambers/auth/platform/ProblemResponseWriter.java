package com.cambers.auth.platform;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;

import java.io.IOException;

/**
 * Writes the service's standardized RFC 9457 response from infrastructure that executes
 * before controller exception handling, such as authentication and rate-limit filters.
 */
public interface ProblemResponseWriter {

    void write(
            HttpServletRequest request,
            HttpServletResponse response,
            HttpStatus status,
            ProblemCode code,
            String detail) throws IOException;
}
