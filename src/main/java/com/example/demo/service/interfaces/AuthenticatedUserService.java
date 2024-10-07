package com.example.demo.service.interfaces;

import com.example.demo.model.entity.User;

public interface AuthenticatedUserService {

    /**
     * Retrieves the currently authenticated user from the security context.
     *
     * @return the {@link User} entity of the authenticated user
     */
    User getAuthenticatedUser();

}
