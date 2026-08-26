package com.cambers.auth.ratelimit.internal;

import com.cambers.auth.observability.SecurityAuditPublisher;
import com.cambers.auth.platform.ProblemResponseWriter;
import com.cambers.auth.ratelimit.ClientIpResolver;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "security.rate-limit",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    static class EnabledRateLimitMvcConfig {

        @Bean
        RateLimitPolicyResolver rateLimitPolicyResolver(RateLimitProperties properties) {
            return new RateLimitPolicyResolver(properties);
        }

        @Bean
        RateLimitInterceptor rateLimitInterceptor(
                RateLimitPolicyResolver policyResolver,
                RequestRateLimiter requestRateLimiter,
                ClientIpResolver clientIpResolver,
                ProblemResponseWriter problemWriter,
                SecurityAuditPublisher auditPublisher) {
            return new RateLimitInterceptor(
                    policyResolver,
                    requestRateLimiter,
                    clientIpResolver,
                    problemWriter,
                    auditPublisher
            );
        }

        @Bean
        WebMvcConfigurer rateLimitWebMvcConfigurer(RateLimitInterceptor rateLimitInterceptor) {
            return new WebMvcConfigurer() {
                @Override
                public void addInterceptors(org.springframework.web.servlet.config.annotation.InterceptorRegistry registry) {
                    registry.addInterceptor(rateLimitInterceptor)
                            .addPathPatterns("/api/v1/auth/**");
                }
            };
        }
    }
}
