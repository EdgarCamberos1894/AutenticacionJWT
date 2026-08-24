package com.cambers.auth.service;

public record SessionClientMetadata(String userAgent, String ipAddress) {

    private static final int MAX_USER_AGENT_LENGTH = 512;
    private static final int MAX_IP_ADDRESS_LENGTH = 45;

    public SessionClientMetadata {
        userAgent = normalize(userAgent, MAX_USER_AGENT_LENGTH);
        ipAddress = normalize(ipAddress, MAX_IP_ADDRESS_LENGTH);
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String stripped = value.strip();
        return stripped.length() <= maxLength ? stripped : stripped.substring(0, maxLength);
    }
}
