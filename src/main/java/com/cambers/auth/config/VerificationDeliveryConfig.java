package com.cambers.auth.config;

import com.cambers.auth.config.properties.VerificationDeliveryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(VerificationDeliveryProperties.class)
public class VerificationDeliveryConfig {
}
