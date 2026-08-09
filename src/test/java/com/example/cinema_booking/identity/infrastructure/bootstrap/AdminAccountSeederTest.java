package com.example.cinema_booking.identity.infrastructure.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.cinema_booking.identity.application.port.out.PasswordHasherPort;
import com.example.cinema_booking.identity.application.port.out.UserRoleAssignment;
import com.example.cinema_booking.identity.domain.UserStatus;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.RoleEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserRoleEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.RoleRepository;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRepository;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRoleRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class AdminAccountSeederTest {

  @Mock private UserRepository userRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private UserRoleRepository userRoleRepository;
  @Mock private PasswordHasherPort passwordHasherPort;

  @Test
  void skipsSeedingWhenEmailIsNotConfigured() {
    AdminAccountSeeder seeder = seederWith(new AdminSeedProperties(" ", "password", "Admin"));

    seeder.run();

    verifyNoInteractions(userRepository, roleRepository, userRoleRepository, passwordHasherPort);
  }

  @Test
  void skipsSeedingWhenPasswordIsNotConfigured() {
    AdminAccountSeeder seeder =
        seederWith(new AdminSeedProperties("admin@example.com", null, "Admin"));

    seeder.run();

    verifyNoInteractions(userRepository, roleRepository, userRoleRepository, passwordHasherPort);
  }

  @Test
  void updatesExistingAdminPasswordAndNameWhenUserAlreadyHoldsAdminRole() {
    AdminAccountSeeder seeder =
        seederWith(new AdminSeedProperties("admin@example.com", "new-password", "New Name"));
    UserEntity existingAdmin = new UserEntity();
    existingAdmin.setId(1L);
    existingAdmin.setEmail("admin@example.com");
    when(userRepository.findByEmailIgnoreCase("admin@example.com"))
        .thenReturn(Optional.of(existingAdmin));
    when(userRoleRepository.findRolesByUserId(1L))
        .thenReturn(List.of(new UserRoleAssignment("ADMIN", null)));
    when(passwordHasherPort.encode("new-password")).thenReturn("new-hash");

    seeder.run();

    ArgumentCaptor<UserEntity> captor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(captor.capture());
    assertThat(captor.getValue().getPasswordHash()).isEqualTo("new-hash");
    assertThat(captor.getValue().getFullName()).isEqualTo("New Name");
    verifyNoInteractions(roleRepository);
  }

  @Test
  void doesNotOverwriteCredentialsWhenExistingUserWithSameEmailIsNotAnAdmin() {
    AdminAccountSeeder seeder =
        seederWith(new AdminSeedProperties("admin@example.com", "new-password", "New Name"));
    UserEntity unrelatedCustomer = new UserEntity();
    unrelatedCustomer.setId(1L);
    unrelatedCustomer.setEmail("admin@example.com");
    unrelatedCustomer.setFullName("Some Customer");
    unrelatedCustomer.setPasswordHash("original-hash");
    when(userRepository.findByEmailIgnoreCase("admin@example.com"))
        .thenReturn(Optional.of(unrelatedCustomer));
    when(userRoleRepository.findRolesByUserId(1L))
        .thenReturn(List.of(new UserRoleAssignment("CUSTOMER", null)));

    seeder.run();

    verify(userRepository, never()).save(any());
    assertThat(unrelatedCustomer.getPasswordHash()).isEqualTo("original-hash");
    assertThat(unrelatedCustomer.getFullName()).isEqualTo("Some Customer");
  }

  @Test
  void doesNotOverwriteCredentialsWhenExistingUserHasNoRolesAtAll() {
    AdminAccountSeeder seeder =
        seederWith(new AdminSeedProperties("admin@example.com", "new-password", "New Name"));
    UserEntity unrelatedUser = new UserEntity();
    unrelatedUser.setId(1L);
    unrelatedUser.setEmail("admin@example.com");
    when(userRepository.findByEmailIgnoreCase("admin@example.com"))
        .thenReturn(Optional.of(unrelatedUser));
    when(userRoleRepository.findRolesByUserId(1L)).thenReturn(List.of());

    seeder.run();

    verify(userRepository, never()).save(any());
  }

  @Test
  void createsNewAdminWithActiveStatusAndAdminRoleWhenNoneExists() {
    AdminAccountSeeder seeder =
        seederWith(new AdminSeedProperties("admin@example.com", "password", "Root Admin"));
    when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
    when(passwordHasherPort.encode("password")).thenReturn("hashed-password");
    UserEntity savedAdmin = new UserEntity();
    savedAdmin.setId(42L);
    when(userRepository.save(any(UserEntity.class))).thenReturn(savedAdmin);
    RoleEntity adminRole = new RoleEntity();
    adminRole.setCode("ADMIN");
    when(roleRepository.getReferenceById("ADMIN")).thenReturn(adminRole);

    seeder.run();

    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getEmail()).isEqualTo("admin@example.com");
    assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo("hashed-password");
    assertThat(userCaptor.getValue().getFullName()).isEqualTo("Root Admin");
    assertThat(userCaptor.getValue().getStatus()).isEqualTo(UserStatus.ACTIVE);

    ArgumentCaptor<UserRoleEntity> roleCaptor = ArgumentCaptor.forClass(UserRoleEntity.class);
    verify(userRoleRepository).save(roleCaptor.capture());
    assertThat(roleCaptor.getValue().getUser()).isSameAs(savedAdmin);
    assertThat(roleCaptor.getValue().getRole()).isSameAs(adminRole);
    assertThat(roleCaptor.getValue().getCinemaId()).isNull();
  }

  @Test
  void defaultsFullNameToAdministratorWhenNotConfigured() {
    AdminAccountSeeder seeder =
        seederWith(new AdminSeedProperties("admin@example.com", "password", "  "));
    when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
    when(passwordHasherPort.encode(anyString())).thenReturn("hashed-password");
    when(userRepository.save(any(UserEntity.class))).thenReturn(new UserEntity());
    when(roleRepository.getReferenceById("ADMIN")).thenReturn(new RoleEntity());

    seeder.run();

    ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
    verify(userRepository).save(userCaptor.capture());
    assertThat(userCaptor.getValue().getFullName()).isEqualTo("Administrator");
  }

  @Test
  void swallowsDataIntegrityViolationWhenConcurrentSeedRaces() {
    AdminAccountSeeder seeder =
        seederWith(new AdminSeedProperties("admin@example.com", "password", "Admin"));
    when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
    when(passwordHasherPort.encode(anyString())).thenReturn("hashed-password");
    when(userRepository.save(any(UserEntity.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate email"));

    assertThatCode(seeder::run).doesNotThrowAnyException();
    verify(userRoleRepository, never()).save(any());
  }

  @Test
  void seedsAdminInsideARequiresNewTransactionSoARaceRollsBackOnItsOwnConnection() {
    RecordingTransactionManager transactionManager = new RecordingTransactionManager();
    AdminAccountSeeder seeder =
        new AdminAccountSeeder(
            userRepository,
            roleRepository,
            userRoleRepository,
            passwordHasherPort,
            new AdminSeedProperties("admin@example.com", "password", "Admin"),
            transactionManager);
    when(userRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.empty());
    when(passwordHasherPort.encode(anyString())).thenReturn("hashed-password");
    when(userRepository.save(any(UserEntity.class)))
        .thenThrow(new DataIntegrityViolationException("duplicate email"));

    seeder.run();

    assertThat(transactionManager.lastDefinition.getPropagationBehavior())
        .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    assertThat(transactionManager.rolledBack).isTrue();
    assertThat(transactionManager.committed).isFalse();
  }

  private AdminAccountSeeder seederWith(AdminSeedProperties properties) {
    return new AdminAccountSeeder(
        userRepository,
        roleRepository,
        userRoleRepository,
        passwordHasherPort,
        properties,
        noopTransactionManager());
  }

  private static PlatformTransactionManager noopTransactionManager() {
    return new PlatformTransactionManager() {
      @Override
      public TransactionStatus getTransaction(TransactionDefinition definition) {
        return new SimpleTransactionStatus();
      }

      @Override
      public void commit(TransactionStatus status) {}

      @Override
      public void rollback(TransactionStatus status) {}
    };
  }

  /** Records whether the seeder's write path commits or rolls back its REQUIRES_NEW transaction. */
  private static final class RecordingTransactionManager implements PlatformTransactionManager {
    private TransactionDefinition lastDefinition;
    private boolean committed;
    private boolean rolledBack;

    @Override
    public TransactionStatus getTransaction(TransactionDefinition definition)
        throws TransactionException {
      this.lastDefinition = definition;
      return new SimpleTransactionStatus();
    }

    @Override
    public void commit(TransactionStatus status) {
      committed = true;
    }

    @Override
    public void rollback(TransactionStatus status) {
      rolledBack = true;
    }
  }
}
