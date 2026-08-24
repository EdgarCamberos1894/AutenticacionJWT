package com.cambers.auth.service;

import com.cambers.auth.dto.RegisterRequest;
import com.cambers.auth.dto.RegistrationResponse;
import com.cambers.auth.entity.RoleName;
import com.cambers.auth.entity.User;
import com.cambers.auth.exception.EmailAlreadyRegisteredException;
import com.cambers.auth.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailNormalizer emailNormalizer;
    private final Clock clock;

    public RegistrationService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            EmailNormalizer emailNormalizer,
            Clock clock) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.emailNormalizer = emailNormalizer;
        this.clock = clock;
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        Instant now = clock.instant();
        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()), now);
        user.assignRole(RoleName.USER, now);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }

        emailVerificationService.issueVerification(user);
        return new RegistrationResponse(user.getId(), user.getEmail(), true);
    }
}
