package com.cambers.auth.platform.internal.http;

import com.cambers.auth.platform.ApiException;
import com.cambers.auth.platform.ProblemCode;
import com.cambers.auth.platform.RetryAfterProvider;
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
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

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
        if (exception instanceof RetryAfterProvider retryAfterProvider) {
            headers.set(HttpHeaders.RETRY_AFTER, Long.toString(Math.max(1, retryAfterProvider.retryAfterSeconds())));
        }
        if (exception.status().is5xxServerError()) {
            log.error("API service failure while processing {}", request.getDescription(false), exception);
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

        List<ValidationError> fieldErrors = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ValidationError(
                        message(error.getDefaultMessage()),
                        "#/" + error.getField().replace('.', '/')
                ))
                .toList();

        List<ValidationError> globalErrors = exception.getBindingResult()
                .getGlobalErrors()
                .stream()
                .map(error -> new ValidationError(message(error.getDefaultMessage()), "#"))
                .toList();

        ProblemDetail problem = validationProblem(mergeValidationErrors(fieldErrors, globalErrors));
        return handleExceptionInternal(exception, problem, headers, HttpStatus.UNPROCESSABLE_CONTENT, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        if (exception.isForReturnValue()) {
            log.error("Controller return-value validation failed while processing {}", request.getDescription(false), exception);
            ProblemDetail problem = problem(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    ProblemCode.INTERNAL_ERROR,
                    "An unexpected error occurred."
            );
            return handleExceptionInternal(
                    exception,
                    problem,
                    headers,
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    request
            );
        }

        Stream<ValidationError> parameterErrors = exception.getParameterValidationResults()
                .stream()
                .flatMap(result -> {
                    String parameterName = result.getMethodParameter().getParameterName();
                    String pointer = parameterName == null || parameterName.isBlank()
                            ? "#"
                            : "#/" + parameterName;
                    return result.getResolvableErrors()
                            .stream()
                            .map(error -> new ValidationError(message(error.getDefaultMessage()), pointer));
                });

        Stream<ValidationError> crossParameterErrors = exception.getCrossParameterValidationResults()
                .stream()
                .map(error -> new ValidationError(message(error.getDefaultMessage()), "#"));

        List<ValidationError> errors = Stream.concat(parameterErrors, crossParameterErrors)
                .sorted(validationErrorComparator())
                .toList();

        ProblemDetail problem = validationProblem(errors);
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
    protected ResponseEntity<Object> createResponseEntity(
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            enrich(problem, codeFor(statusCode));
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    private ProblemDetail validationProblem(List<ValidationError> errors) {
        ProblemDetail problem = problem(
                HttpStatus.UNPROCESSABLE_CONTENT,
                ProblemCode.VALIDATION_ERROR,
                "One or more request fields are invalid."
        );
        problem.setProperty("errors", errors);
        return problem;
    }

    private List<ValidationError> mergeValidationErrors(
            List<ValidationError> fieldErrors,
            List<ValidationError> globalErrors) {
        return Stream.concat(fieldErrors.stream(), globalErrors.stream())
                .sorted(validationErrorComparator())
                .toList();
    }

    private Comparator<ValidationError> validationErrorComparator() {
        return Comparator.comparing(ValidationError::pointer)
                .thenComparing(ValidationError::detail);
    }

    private String message(String defaultMessage) {
        return defaultMessage == null ? "Invalid value" : defaultMessage;
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
            case 406 -> ProblemCode.NOT_ACCEPTABLE;
            case 409 -> ProblemCode.CONFLICT;
            case 415 -> ProblemCode.UNSUPPORTED_MEDIA_TYPE;
            case 422 -> ProblemCode.VALIDATION_ERROR;
            case 429 -> ProblemCode.RATE_LIMIT_EXCEEDED;
            case 503 -> ProblemCode.SERVICE_UNAVAILABLE;
            default -> statusCode.is4xxClientError()
                    ? ProblemCode.INVALID_REQUEST
                    : ProblemCode.INTERNAL_ERROR;
        };
    }
}
