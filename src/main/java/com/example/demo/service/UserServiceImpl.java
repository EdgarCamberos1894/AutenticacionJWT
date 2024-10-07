package com.example.demo.service;

import com.example.demo.dto.mapper.UserMapper;
import com.example.demo.dto.request.EmailReq;
import com.example.demo.dto.request.PasswordChangeReq;
import com.example.demo.dto.request.UserReq;
import com.example.demo.dto.response.UserRes;
import com.example.demo.exception.IncorrectCurrentPasswordException;
import com.example.demo.exception.UserNotFoundException;
import com.example.demo.model.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.interfaces.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;


@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

  private final UserMapper userMapper;
  private final UserRepository userRepository;
  private final PasswordEncoder passwordEncoder;
  private final RoleService roleService;
  private final UserProfileService userProfileService;
  private final EmailService emailService;
  private final AuthenticatedUserService authenticatedUserService;


  @Override
  @Transactional
  public UserRes create(UserReq userReq) {
    User user = userMapper.toEntity(userReq);
    user.setPassword(passwordEncoder.encode(userReq.password()));
    user.setRole(roleService.getByName("USER"));
    User savedUser = userRepository.save(user);

    userProfileService.create(savedUser,userReq.name(),userReq.lastname());

    emailService.sendVerificationEmail(new EmailReq(savedUser.getEmail()));

    return userMapper.toResponse(savedUser);
  }


  @Override
  public User getByEmail(String email) {
    return userRepository.findByEmail(email)
            .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
  }


  @Transactional
  @Override
  public void changePassword(PasswordChangeReq request) {
    User user = authenticatedUserService.getAuthenticatedUser();
    if (!passwordEncoder.matches(request.oldPassword(), user.getPassword())) {
      throw new IncorrectCurrentPasswordException("Current password is incorrect");
    }
    user.setPassword(passwordEncoder.encode(request.newPassword()));
    userRepository.save(user);
  }

}
