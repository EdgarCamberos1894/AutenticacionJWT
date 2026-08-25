package com.cambers.auth.ratelimit;

import jakarta.servlet.http.HttpServletRequest;

public interface ClientIpResolver {

    String resolve(HttpServletRequest request);
}
