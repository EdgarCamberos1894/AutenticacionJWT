package com.cambers.auth.config;

import com.cambers.auth.config.properties.AuthMailProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthMailProperties.class)
public class AuthMailConfig {
}
