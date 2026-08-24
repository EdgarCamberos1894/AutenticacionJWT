package com.cambers.auth.ratelimit;

import com.cambers.auth.exception.ProblemCode;
import com.cambers.auth.security.SecurityProblemWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;

public class RateLimitInterceptor implements HandlerInterceptor {

    private final RateLimitPolicyResolver policyResolver;
    private final RequestRateLimiter requestRateLimiter;
    private final ClientIpResolver clientIpResolver;
    private final SecurityProblemWriter problemWriter;

    public RateLimitInterceptor(
            RateLimitPolicyResolver policyResolver,
            RequestRateLimiter requestRateLimiter,
            ClientIpResolver clientIpResolver,
            SecurityProblemWriter problemWriter) {
        this.policyResolver = policyResolver;
        this.requestRateLimiter = requestRateLimiter;
        this.clientIpResolver = clientIpResolver;
        this.problemWriter = problemWriter;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler) throws Exception {

        if (!HttpMethod.POST.matches(request.getMethod())) {
            return true;
        }

        RateLimitPolicyResolver.NamedPolicy namedPolicy = policyResolver.resolve(requestPath(request))
                .orElse(null);
        if (namedPolicy == null) {
            return true;
        }

        try {
            RateLimitDecision decision = requestRateLimiter.consume(
                    namedPolicy.name(),
                    clientIpResolver.resolve(request),
                    namedPolicy.policy()
            );
            if (decision.allowed()) {
                return true;
            }

            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
            problemWriter.write(
                    request,
                    response,
                    HttpStatus.TOO_MANY_REQUESTS,
                    ProblemCode.RATE_LIMIT_EXCEEDED,
                    "Too many requests. Retry after the indicated delay."
            );
            return false;
        } catch (RateLimitBackendUnavailableException exception) {
            response.setHeader(HttpHeaders.RETRY_AFTER, "1");
            problemWriter.write(
                    request,
                    response,
                    HttpStatus.SERVICE_UNAVAILABLE,
                    ProblemCode.SERVICE_UNAVAILABLE,
                    "The authentication service is temporarily unable to enforce request limits."
            );
            return false;
        }
    }

    private String requestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();

        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }
}
