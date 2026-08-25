package com.cambers.auth.account;

public interface PasswordRecovery {

    void requestReset(String email);

    void confirmReset(String rawToken, String newPassword);
}
