package com.cambers.auth.exception;

import com.cambers.auth.ratelimit.RateLimitBackendUnavailableException;
import com.cambers.auth.ratelimit.RateLimitExceededException;
import com.cambers.auth.validation.ValidationError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;

    public GlobalExceptionHandler(Clock clock) {
        this.clock = clock;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Object> handleApiException(ApiException exception, WebRequest request) {
        ProblemDetail problem = problem(exception.status(), exception.code(), exception.getMessage());
        HttpHeaders headers = new HttpHeaders();

        if (exception.status() == HttpStatus.UNAUTHORIZED) {
            headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        }
        if (exception instanceof RateLimitExceededException rateLimitExceededException) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(rateLimitExceededException.retryAfterSeconds()));
        }
        if (exception instanceof RateLimitBackendUnavailableException) {
            headers.set(HttpHeaders.RETRY_AFTER, "1");
            log.error("Rate-limit backend unavailable while processing {}", request.getDescription(false), exception);
        }

        return handleExceptionInternal(exception, problem, headers, exception.status(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> handleUnexpectedException(Exception exception, WebRequest request) {
        log.error("Unhandled exception while processing {}", request.getDescription(false), exception);
        ProblemDetail problem = problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ProblemCode.INTERNAL_ERROR,
                "An unexpected error occurred."
        );
        return handleExceptionInternal(
                exception,
                problem,
                HttpHeaders.EMPTY,
                HttpStatus.INTERNAL_SERVER_ERROR,
                request
        );
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ValidationError> errors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .sorted(Comparator.comparing(error -> error.getField()))
                .map(error -> new ValidationError(
                        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        "#/" + error.getField().replace('.', '/')
                ))
                .toList();

        ProblemDetail problem = problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ProblemCode.VALIDATION_ERROR,
                "One or more request fields are invalid."
        );
        problem.setProperty("errors", errors);
        return handleExceptionInternal(exception, problem, headers, HttpStatus.UNPROCESSABLE_CONTENT, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.BAD_REQUEST,
                ProblemCode.INVALID_REQUEST,
                "The request body is malformed or cannot be read."
        );
        return handleExceptionInternal(exception, problem, headers, HttpStatus.BAD_REQUEST, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.METHOD_NOT_ALLOWED,
                ProblemCode.METHOD_NOT_ALLOWED,
                "The HTTP method is not supported for this resource."
        );
        return handleExceptionInternal(exception, problem, headers, HttpStatus.METHOD_NOT_ALLOWED, request);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        ProblemDetail problem = problem(
                HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                ProblemCode.UNSUPPORTED_MEDIA_TYPE,
                "The request media type is not supported."
        );
        return handleExceptionInternal(exception, problem, headers, HttpStatus.UNSUPPORTED_MEDIA_TYPE, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        Object safeBody = body;
        if (body instanceof ProblemDetail problem) {
            ProblemCode code = codeFor(statusCode);
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                enrich(problem, code);
            }
            safeBody = problem;
        }
        return super.handleExceptionInternal(exception, safeBody, headers, statusCode, request);
    }

    private ProblemDetail problem(HttpStatus status, ProblemCode code, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        enrich(problem, code);
        return problem;
    }

    private void enrich(ProblemDetail problem, ProblemCode code) {
        problem.setType(code.type());
        problem.setTitle(code.title());
        problem.setProperty("code", code.value());
        problem.setProperty("timestamp", clock.instant());
    }

    private ProblemCode codeFor(HttpStatusCode statusCode) {
        return switch (statusCode.value()) {
            case 400 -> ProblemCode.INVALID_REQUEST;
            case 401 -> ProblemCode.AUTHENTICATION_REQUIRED;
            case 403 -> ProblemCode.ACCESS_DENIED;
            case 404 -> ProblemCode.RESOURCE_NOT_FOUND;
            case 405 -> ProblemCode.METHOD_NOT_ALLOWED;
            case 409 -> ProblemCode.CONFLICT;
            case 415 -> ProblemCode.UNSUPPORTED_MEDIA_TYPE;
            case 422 -> ProblemCode.VALIDATION_ERROR;
            case 429 -> ProblemCode.RATE_LIMIT_EXCEEDED;
            case 503 -> ProblemCode.SERVICE_UNAVAILABLE;
            default -> ProblemCode.INTERNAL_ERROR;
        };
    }
}
