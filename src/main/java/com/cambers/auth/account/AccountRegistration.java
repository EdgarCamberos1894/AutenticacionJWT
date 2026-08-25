package com.cambers.auth.account;

import com.cambers.auth.dto.RegisterRequest;
import com.cambers.auth.dto.RegistrationResponse;

public interface AccountRegistration {

    RegistrationResponse register(RegisterRequest request);
}
