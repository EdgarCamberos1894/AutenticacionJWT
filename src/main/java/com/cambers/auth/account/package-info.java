@org.springframework.modulith.ApplicationModule(
        id = "account",
        displayName = "Account Lifecycle",
        allowedDependencies = {"delivery", "observability", "abuse", "platform"}
)
package com.cambers.auth.account;
