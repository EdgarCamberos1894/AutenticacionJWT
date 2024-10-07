package com.example.demo.service;

import com.example.demo.dto.request.*;
import com.example.demo.dto.response.LoginRes;
import com.example.demo.dto.response.UserRes;
import com.example.demo.exception.InvalidAuthorizationHeaderException;
import com.example.demo.exception.InvalidTokenPurposeException;
import com.example.demo.exception.TokenExpiredException;
import com.example.demo.exception.UserNotVerifiedException;
import com.example.demo.model.entity.User;
import com.example.demo.model.enums.TokenPurpose;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.interfaces.AuthService;
import com.example.demo.service.interfaces.EmailService;
import com.example.demo.service.interfaces.TokenBlacklistService;
import com.example.demo.service.interfaces.UserService;
import com.example.demo.security.jwt.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

  private final UserService userService;
  private final AuthenticationManager authenticationManager;
  private final PasswordEncoder passwordEncoder;
  private final UserRepository userRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final EmailService emailService;
  private final TokenBlacklistService tokenBlacklistService;
  private final AuthenticatedUserServiceImpl authenticatedUserService;

  @Value("${frontend.baseUrl}")
  private String baseUrl;

  @Value("${frontend.verifyEmailUrl}")
  private String resetPasswordUrl;

  @Override
  public LoginRes login(LoginReq loginReq) {
    authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(loginReq.email(), loginReq.password()));
    User user = userService.getByEmail(loginReq.email());

    if(!user.isVerify()) {
      throw new UserNotVerifiedException("User is not verified");
    }

    String token = this.jwtTokenProvider.generateSessionToken(user);
    return new LoginRes(user.getId(), user.getRole().getName(), token);
  }

  @Override
  public UserRes register(UserReq userReq) {
    return this.userService.create(userReq);
  }

  @Transactional
  @Override
  public void forgotPassword(EmailReq request) {
    User user = this.userService.getByEmail(request.email());
    String token = jwtTokenProvider.generateTokenForPurpose(user, TokenPurpose.RESET_PASSWORD);

    Map<String, Object> templateModel = new HashMap<>();
    templateModel.put("name", user.getProfile().getName());
    templateModel.put("recoveryPasswordUrl",baseUrl+resetPasswordUrl+"?token="+token);

    emailService.sendEmail(request.email(),
            "We Received a Password Reset Request for Your Account",
            templateModel,
            "password-recovery");
  }

  @Transactional
  @Override
  public void resetPassword(PasswordResetReq request) {
    validateToken(request.token(), TokenPurpose.RESET_PASSWORD);
    String email = jwtTokenProvider.getUsernameFromToken(request.token());
    User user = userService.getByEmail(email);
    user.setPassword(passwordEncoder.encode(request.password()));
    userRepository.save(user);
    tokenBlacklistService.addToBlacklist(request.token(),user);
  }

  @Transactional
  @Override
  public void verifyEmail(EmailVerificationReq request) {
    validateToken(request.token(), TokenPurpose.VERIFY_EMAIL);
    String email = jwtTokenProvider.getUsernameFromToken(request.token());
    User user = userService.getByEmail(email);
    user.setVerify(true);
    userRepository.save(user);
    tokenBlacklistService.addToBlacklist(request.token(),user);
  }


  private void validateToken(String token, TokenPurpose expectedPurpose) {
    if (jwtTokenProvider.isTokenExpired(token)) {
      throw new TokenExpiredException("Token has expired");
    }
    String purposeToken = jwtTokenProvider.getPurposeFromToken(token);
    if (!expectedPurpose.name().equals(purposeToken)) {
      throw new InvalidTokenPurposeException("This token cannot be used for this purpose.");
    }
  }

  @Transactional
  @Override
  public void resendVerificationEmail(EmailReq request) {emailService.sendVerificationEmail(request);}

  @Transactional
  @Override
  public void logout(HttpServletRequest request) {
    String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (authHeader != null && authHeader.startsWith("Bearer ")) {
      String token = authHeader.substring(7);
      User user = authenticatedUserService.getAuthenticatedUser();
      tokenBlacklistService.addToBlacklist(token, user);
    }
    else
      throw new InvalidAuthorizationHeaderException("Authorization header is invalid");
  }
}
