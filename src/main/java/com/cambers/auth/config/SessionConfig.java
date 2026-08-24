package com.cambers.auth.config;

import com.cambers.auth.config.properties.SessionProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(SessionProperties.class)
public class SessionConfig {
}
