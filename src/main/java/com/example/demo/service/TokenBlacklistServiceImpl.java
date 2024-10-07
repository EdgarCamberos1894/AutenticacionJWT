package com.example.demo.service;

import com.example.demo.exception.InvalidTokenException;
import com.example.demo.model.entity.TokenBlacklist;
import com.example.demo.model.entity.User;
import com.example.demo.repository.TokenBlacklistRepository;
import com.example.demo.security.jwt.JwtTokenProvider;
import com.example.demo.service.interfaces.TokenBlacklistService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;


@Service
@RequiredArgsConstructor
public class TokenBlacklistServiceImpl implements TokenBlacklistService {

    @Lazy
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistRepository tokenBlacklistRepository;

    @Transactional
    @Override
    public void addToBlacklist(String token, User user) {
        if (!jwtTokenProvider.isTokenValid(token, user)){
            throw new InvalidTokenException("Token is not valid");
        }
        TokenBlacklist tokenBlacklist = new TokenBlacklist();
        tokenBlacklist.setToken(token);
        tokenBlacklist.setExpirationDate(jwtTokenProvider.getTokenExpirationDate(token));
        tokenBlacklistRepository.save(tokenBlacklist);
    }

    @Transactional
    public boolean isTokenInBlacklist(String token) {
        return tokenBlacklistRepository.existsByToken(token);
    }


    @Transactional
    @Scheduled(cron = "0 0 */12 * * ?") // Ejecuta cada 12 horas
    public void cleanupExpiredTokens() {
        LocalDateTime now = LocalDateTime.now();
        tokenBlacklistRepository.deleteExpiredTokens(now);
    }




}
