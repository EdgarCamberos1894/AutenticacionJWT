package com.cambers.auth.config;

import com.cambers.auth.config.properties.OneTimeTokenProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(OneTimeTokenProperties.class)
public class OneTimeTokenConfig {
}
