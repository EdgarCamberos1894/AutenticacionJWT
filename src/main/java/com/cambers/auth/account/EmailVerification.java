package com.cambers.auth.account;

public interface EmailVerification {

    void resend(String email);

    void confirm(String rawToken);
}
