package com.cambers.auth.config.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "security.client-ip")
public record ClientIpProperties(List<String> trustedProxyAddresses) {

    public ClientIpProperties {
        trustedProxyAddresses = trustedProxyAddresses == null
                ? List.of()
                : trustedProxyAddresses.stream()
                        .filter(address -> address != null && !address.isBlank())
                        .map(String::strip)
                        .distinct()
                        .toList();
    }
}
