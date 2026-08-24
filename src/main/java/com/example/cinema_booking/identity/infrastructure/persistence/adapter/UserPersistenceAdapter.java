package com.example.cinema_booking.identity.infrastructure.persistence.adapter;

import com.example.cinema_booking.identity.application.port.out.LoadUserPort;
import com.example.cinema_booking.identity.application.port.out.LoadUserRolesPort;
import com.example.cinema_booking.identity.application.port.out.SaveUserPort;
import com.example.cinema_booking.identity.application.port.out.UserRoleAssignment;
import com.example.cinema_booking.identity.domain.User;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.RoleEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserRoleEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.RoleRepository;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRepository;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class UserPersistenceAdapter implements LoadUserPort, LoadUserRolesPort, SaveUserPort {

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final RoleRepository roleRepository;

  public UserPersistenceAdapter(
      UserRepository userRepository,
      UserRoleRepository userRoleRepository,
      RoleRepository roleRepository) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
    this.roleRepository = roleRepository;
  }

  @Override
  public Optional<User> loadByIdentifier(String identifier) {
    Optional<UserEntity> entity =
        identifier.contains("@")
            ? userRepository.findByEmailIgnoreCase(identifier)
            : userRepository.findByPhone(identifier);
    return entity.map(UserPersistenceAdapter::toDomain);
  }

  @Override
  @Transactional
  public User save(User user, String roleCode) {
    UserEntity entity = new UserEntity();
    entity.setEmail(user.getEmail());
    entity.setPhone(user.getPhone());
    entity.setPasswordHash(user.getPasswordHash());
    entity.setFullName(user.getFullName());
    entity.setDateOfBirth(user.getDateOfBirth());
    entity.setStatus(user.getStatus());
    entity = userRepository.save(entity);

    // The unique indexes on lower(email)/phone are the race-safe backstop: two concurrent
    // registrations that both pass the exists-check still cannot commit duplicates.
    RoleEntity role = roleRepository.getReferenceById(roleCode);
    UserRoleEntity userRole = new UserRoleEntity();
    userRole.setUser(entity);
    userRole.setRole(role);
    userRole.setCinemaId(null);
    userRoleRepository.save(userRole);

    return toDomain(entity);
  }

  @Override
  public boolean existsByEmailIgnoreCase(String email) {
    return userRepository.existsByEmailIgnoreCase(email);
  }

  @Override
  public boolean existsByPhone(String phone) {
    return userRepository.existsByPhone(phone);
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
