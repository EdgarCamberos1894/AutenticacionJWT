package com.cambers.auth.authentication;

import com.cambers.auth.dto.LoginRequest;
import com.cambers.auth.dto.RefreshTokenRequest;
import com.cambers.auth.dto.TokenPairResponse;

public interface SessionAuthentication {

    TokenPairResponse login(LoginRequest request, AuthenticationClientMetadata clientMetadata);

    TokenPairResponse refresh(RefreshTokenRequest request);
}
