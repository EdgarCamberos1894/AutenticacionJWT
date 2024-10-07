package com.example.demo.service.interfaces;

import com.example.demo.dto.request.*;
import com.example.demo.dto.response.LoginRes;
import com.example.demo.dto.response.UserRes;
import jakarta.servlet.http.HttpServletRequest;

/**
 * AuthService defines the contract for user authentication and registration operations.
 * This interface provides methods for logging in, registering users, and password-related operations.
 */
public interface AuthService {

  /**
   * Authenticates the user based on email and password.
   *
   * @param request the DTO containing the login credentials (email and password)
   * @return a {@link LoginRes} object containing the login result
   */
  LoginRes login(LoginReq request);

  /**
   * Registers a new user in the system.
   *
   * @param request the DTO containing the user registration data
   * @return a {@link UserRes} object with the user's information after registration
   */
  UserRes register(UserReq request);

  /**
   * Sends a password reset token to the user's email.
   *
   * @param request the DTO containing the user's email address
   */
  void forgotPassword(EmailReq request);

  /**
   * Resets the user's password using the provided token.
   *
   * @param request the DTO containing the reset token and the new password
   */
  void resetPassword(PasswordResetReq request);

  /**
   * Verifies the user's email using a verification token.
   *
   * @param request the DTO containing the email verification token
   */
  void verifyEmail(EmailVerificationReq request);

  /**
   * Resends the email verification link to the user.
   *
   * @param request the DTO containing the user's email address
   */
  void resendVerificationEmail(EmailReq request);

  void logout(HttpServletRequest request);
}
