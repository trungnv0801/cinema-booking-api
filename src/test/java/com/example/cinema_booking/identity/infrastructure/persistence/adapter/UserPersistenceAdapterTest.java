package com.example.cinema_booking.identity.infrastructure.persistence.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cinema_booking.identity.application.port.out.UserRoleAssignment;
import com.example.cinema_booking.identity.domain.User;
import com.example.cinema_booking.identity.domain.UserStatus;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRepository;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRoleRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserPersistenceAdapterTest {

  @Mock private UserRepository userRepository;
  @Mock private UserRoleRepository userRoleRepository;

  private UserPersistenceAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new UserPersistenceAdapter(userRepository, userRoleRepository);
  }

  private static UserEntity fullUserEntity() {
    UserEntity entity = new UserEntity();
    entity.setId(1L);
    entity.setPublicId(UUID.randomUUID());
    entity.setEmail("user@example.com");
    entity.setPhone("0900000000");
    entity.setPasswordHash("hashed-password");
    entity.setFullName("Jane Doe");
    entity.setDateOfBirth(LocalDate.of(1995, 1, 1));
    entity.setStatus(UserStatus.ACTIVE);
    entity.setEmailVerifiedAt(OffsetDateTime.now().minusDays(1));
    entity.setCreatedAt(OffsetDateTime.now().minusDays(10));
    entity.setUpdatedAt(OffsetDateTime.now());
    return entity;
  }

  @Test
  void loadByIdentifierLooksUpByEmailWhenIdentifierContainsAtSign() {
    UserEntity entity = fullUserEntity();
    when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(entity));

    Optional<User> result = adapter.loadByIdentifier("user@example.com");

    assertThat(result).isPresent();
    verify(userRepository, never()).findByPhone(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void loadByIdentifierLooksUpByPhoneWhenIdentifierHasNoAtSign() {
    UserEntity entity = fullUserEntity();
    when(userRepository.findByPhone("0900000000")).thenReturn(Optional.of(entity));

    Optional<User> result = adapter.loadByIdentifier("0900000000");

    assertThat(result).isPresent();
    verify(userRepository, never()).findByEmailIgnoreCase(org.mockito.ArgumentMatchers.anyString());
  }

  @Test
  void loadByIdentifierReturnsEmptyWhenNoUserFound() {
    when(userRepository.findByEmailIgnoreCase("missing@example.com")).thenReturn(Optional.empty());

    assertThat(adapter.loadByIdentifier("missing@example.com")).isEmpty();
  }

  @Test
  void loadByIdentifierMapsAllFieldsToDomainUser() {
    UserEntity entity = fullUserEntity();
    when(userRepository.findByEmailIgnoreCase("user@example.com")).thenReturn(Optional.of(entity));

    User user = adapter.loadByIdentifier("user@example.com").orElseThrow();

    assertThat(user.getId()).isEqualTo(entity.getId());
    assertThat(user.getPublicId()).isEqualTo(entity.getPublicId());
    assertThat(user.getEmail()).isEqualTo(entity.getEmail());
    assertThat(user.getPhone()).isEqualTo(entity.getPhone());
    assertThat(user.getPasswordHash()).isEqualTo(entity.getPasswordHash());
    assertThat(user.getFullName()).isEqualTo(entity.getFullName());
    assertThat(user.getDateOfBirth()).isEqualTo(entity.getDateOfBirth());
    assertThat(user.getStatus()).isEqualTo(entity.getStatus());
    assertThat(user.getEmailVerifiedAt()).isEqualTo(entity.getEmailVerifiedAt());
    assertThat(user.getCreatedAt()).isEqualTo(entity.getCreatedAt());
    assertThat(user.getUpdatedAt()).isEqualTo(entity.getUpdatedAt());
  }

  @Test
  void loadRolesDelegatesToUserRoleRepository() {
    List<UserRoleAssignment> roles = List.of(new UserRoleAssignment("CUSTOMER", null));
    when(userRoleRepository.findRolesByUserId(1L)).thenReturn(roles);

    assertThat(adapter.loadRoles(1L)).isEqualTo(roles);
  }
}
