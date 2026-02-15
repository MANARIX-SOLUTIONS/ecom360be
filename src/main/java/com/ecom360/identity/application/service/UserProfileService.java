package com.ecom360.identity.application.service;

import com.ecom360.identity.application.dto.UserProfileRequest;
import com.ecom360.identity.application.dto.UserProfileResponse;
import com.ecom360.identity.domain.model.User;
import com.ecom360.identity.domain.repository.UserRepository;
import com.ecom360.identity.infrastructure.security.UserPrincipal;
import com.ecom360.shared.domain.exception.ResourceAlreadyExistsException;
import com.ecom360.shared.domain.exception.ResourceNotFoundException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {

  private final UserRepository userRepository;

  public UserProfileService(UserRepository userRepository) {
    this.userRepository = userRepository;
  }

  public UserProfileResponse get(UserPrincipal p) {
    User u = findUser(p.userId());
    return new UserProfileResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone());
  }

  @Transactional
  public UserProfileResponse update(UserProfileRequest req, UserPrincipal p) {
    User u = findUser(p.userId());
    if (!req.email().equalsIgnoreCase(u.getEmail())) {
      if (userRepository.existsByEmail(req.email())) {
        throw new ResourceAlreadyExistsException("User", req.email());
      }
    }
    u.setFullName(req.fullName());
    u.setEmail(req.email());
    u.setPhone(req.phone());
    u = userRepository.save(u);
    return new UserProfileResponse(u.getId(), u.getFullName(), u.getEmail(), u.getPhone());
  }

  private User findUser(UUID userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User", userId));
  }
}
