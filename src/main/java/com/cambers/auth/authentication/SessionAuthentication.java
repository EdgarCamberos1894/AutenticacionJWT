package com.cambers.auth.authentication;

public interface SessionAuthentication {

    TokenPairResponse login(LoginRequest request, AuthenticationClientMetadata clientMetadata);

    TokenPairResponse refresh(RefreshTokenRequest request);
}
