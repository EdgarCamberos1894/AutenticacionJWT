@org.springframework.modulith.ApplicationModule(
        id = "authentication",
        displayName = "Authentication and Sessions",
        allowedDependencies = {"account", "abuse", "observability", "platform"}
)
package com.cambers.auth.authentication;
