@org.springframework.modulith.ApplicationModule(
        id = "authentication",
        displayName = "Authentication and Sessions",
        allowedDependencies = {"account", "abuse", "observability"}
)
package com.cambers.auth.authentication;
