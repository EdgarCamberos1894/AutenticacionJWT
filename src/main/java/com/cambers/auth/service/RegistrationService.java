package com.cambers.auth.service;

import com.cambers.auth.dto.RegisterRequest;
import com.cambers.auth.dto.RegistrationResponse;
import com.cambers.auth.entity.Role;
import com.cambers.auth.entity.User;
import com.cambers.auth.enums.RoleName;
import com.cambers.auth.exception.EmailAlreadyRegisteredException;
import com.cambers.auth.repository.RoleRepository;
import com.cambers.auth.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final EmailNormalizer emailNormalizer;

    public RegistrationService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            EmailNormalizer emailNormalizer) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.emailNormalizer = emailNormalizer;
    }

    @Transactional
    public RegistrationResponse register(RegisterRequest request) {
        String normalizedEmail = emailNormalizer.normalize(request.email());
        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            throw new EmailAlreadyRegisteredException();
        }

        Role userRole = roleRepository.findById(RoleName.USER)
                .orElseThrow(() -> new IllegalStateException("USER role is not configured"));

        User user = new User(normalizedEmail, passwordEncoder.encode(request.password()));
        user.addRole(userRole);

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            throw new EmailAlreadyRegisteredException();
        }

        emailVerificationService.issueVerification(user);
        return new RegistrationResponse(user.getId(), user.getEmail(), true);
    }
}
