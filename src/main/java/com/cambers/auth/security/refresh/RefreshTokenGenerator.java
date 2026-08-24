package com.cambers.auth.security.refresh;

import com.cambers.auth.security.token.GeneratedOpaqueToken;
import com.cambers.auth.security.token.SecureOpaqueTokenGenerator;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenGenerator {

    private final SecureOpaqueTokenGenerator opaqueTokenGenerator;

    public RefreshTokenGenerator(SecureOpaqueTokenGenerator opaqueTokenGenerator) {
        this.opaqueTokenGenerator = opaqueTokenGenerator;
    }

    public GeneratedRefreshToken generate() {
        GeneratedOpaqueToken token = opaqueTokenGenerator.generate();
        return new GeneratedRefreshToken(token.value(), token.hash());
    }

    public String hash(String rawToken) {
        return opaqueTokenGenerator.hash(rawToken);
    }
}
