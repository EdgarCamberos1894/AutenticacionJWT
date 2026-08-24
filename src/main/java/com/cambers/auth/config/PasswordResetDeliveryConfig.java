package com.cambers.auth.config;

import com.cambers.auth.config.properties.PasswordResetDeliveryProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PasswordResetDeliveryProperties.class)
public class PasswordResetDeliveryConfig {
}
