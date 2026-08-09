package com.example.cinema_booking.identity.infrastructure.persistence.adapter;

import com.example.cinema_booking.identity.application.port.out.LoadUserPort;
import com.example.cinema_booking.identity.application.port.out.LoadUserRolesPort;
import com.example.cinema_booking.identity.application.port.out.UserRoleAssignment;
import com.example.cinema_booking.identity.domain.User;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRepository;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class UserPersistenceAdapter implements LoadUserPort, LoadUserRolesPort {

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;

  public UserPersistenceAdapter(
      UserRepository userRepository, UserRoleRepository userRoleRepository) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
  }

  @Override
  public Optional<User> loadByIdentifier(String identifier) {
    Optional<UserEntity> entity =
        identifier.contains("@")
            ? userRepository.findByEmailIgnoreCase(identifier)
            : userRepository.findByPhone(identifier);
    return entity.map(UserPersistenceAdapter::toDomain);
  }

  private static User toDomain(UserEntity entity) {
    return User.builder()
        .id(entity.getId())
        .publicId(entity.getPublicId())
        .email(entity.getEmail())
        .phone(entity.getPhone())
        .passwordHash(entity.getPasswordHash())
        .fullName(entity.getFullName())
        .dateOfBirth(entity.getDateOfBirth())
        .status(entity.getStatus())
        .emailVerifiedAt(entity.getEmailVerifiedAt())
        .createdAt(entity.getCreatedAt())
        .updatedAt(entity.getUpdatedAt())
        .build();
  }

  @Override
  public List<UserRoleAssignment> loadRoles(Long userId) {
    return userRoleRepository.findRolesByUserId(userId);
  }
}
