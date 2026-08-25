package com.cambers.auth.authentication;

import java.util.List;
import java.util.UUID;

public interface SessionManagement {

    List<AuthSessionResponse> listActiveSessions(UUID userId, UUID currentSessionId);

    void logoutCurrent(UUID userId, UUID sessionId);

    void logoutAll(UUID userId);

    void revokeSession(UUID userId, UUID sessionId);
}
