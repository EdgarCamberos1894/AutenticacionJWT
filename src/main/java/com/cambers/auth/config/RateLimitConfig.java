package com.cambers.auth.config;

import com.cambers.auth.config.properties.RateLimitProperties;
import com.cambers.auth.ratelimit.RateLimitInterceptor;
import com.cambers.auth.ratelimit.RateLimitPolicyResolver;
import com.cambers.auth.ratelimit.RequestRateLimiter;
import com.cambers.auth.security.SecurityProblemWriter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RateLimitProperties.class)
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
                SecurityProblemWriter problemWriter) {
            return new RateLimitInterceptor(policyResolver, requestRateLimiter, problemWriter);
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
