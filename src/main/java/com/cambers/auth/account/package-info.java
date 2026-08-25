@org.springframework.modulith.ApplicationModule(
        id = "account",
        displayName = "Account Lifecycle",
        allowedDependencies = {"delivery", "observability", "abuse"}
)
package com.cambers.auth.account;
