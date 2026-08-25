@org.springframework.modulith.ApplicationModule(
        id = "abuse",
        displayName = "Authentication Abuse Protection",
        allowedDependencies = {"observability"}
)
package com.cambers.auth.ratelimit;
