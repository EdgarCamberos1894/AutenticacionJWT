package com.cambers.auth.ratelimit.internal;

import com.cambers.auth.ratelimit.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
class DefaultClientIpResolver implements ClientIpResolver {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final Set<String> trustedProxyAddresses;

    DefaultClientIpResolver(ClientIpProperties properties) {
        this.trustedProxyAddresses = new HashSet<>(properties.trustedProxyAddresses());
    }

    @Override
    public String resolve(HttpServletRequest request) {
        String remoteAddress = normalize(request.getRemoteAddr());
        if (remoteAddress == null) {
            return "unknown";
        }
        if (!trustedProxyAddresses.contains(remoteAddress)) {
            return remoteAddress;
        }

        String forwardedFor = request.getHeader(X_FORWARDED_FOR);
        if (forwardedFor == null || forwardedFor.isBlank()) {
            return remoteAddress;
        }

        String currentHop = remoteAddress;
        String[] forwardedAddresses = forwardedFor.split(",");
        for (int index = forwardedAddresses.length - 1; index >= 0; index--) {
            if (!trustedProxyAddresses.contains(currentHop)) {
                break;
            }

            String previousHop = normalize(forwardedAddresses[index]);
            if (previousHop == null) {
                break;
            }
            currentHop = previousHop;
        }
        return currentHop;
    }

    private String normalize(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        return address.strip();
    }
}
