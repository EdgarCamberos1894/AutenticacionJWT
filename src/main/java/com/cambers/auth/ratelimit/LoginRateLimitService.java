package com.cambers.auth.ratelimit;

public interface LoginRateLimitService {

    void checkAccount(String normalizedEmail);
}
