package com.example.demo.service.interfaces;

import com.example.demo.model.entity.User;

public interface TokenBlacklistService {

    void addToBlacklist(String token, User username);

    boolean isTokenInBlacklist(String token);
}
