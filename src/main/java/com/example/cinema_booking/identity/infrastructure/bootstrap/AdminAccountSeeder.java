package com.example.cinema_booking.identity.infrastructure.bootstrap;

import com.example.cinema_booking.identity.application.port.out.PasswordHasherPort;
import com.example.cinema_booking.identity.application.port.out.UserRoleAssignment;
import com.example.cinema_booking.identity.domain.UserStatus;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.RoleEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserRoleEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.RoleRepository;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRepository;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRoleRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Component
public class AdminAccountSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(AdminAccountSeeder.class);
  private static final String ADMIN_ROLE_CODE = "ADMIN";

  private final UserRepository userRepository;
  private final RoleRepository roleRepository;
  private final UserRoleRepository userRoleRepository;
  private final PasswordHasherPort passwordHasherPort;
  private final AdminSeedProperties adminProperties;
  private final TransactionTemplate requiresNewTransactionTemplate;

  public AdminAccountSeeder(
      UserRepository userRepository,
      RoleRepository roleRepository,
      UserRoleRepository userRoleRepository,
      PasswordHasherPort passwordHasherPort,
      AdminSeedProperties adminProperties,
      PlatformTransactionManager transactionManager) {
    this.userRepository = userRepository;
    this.roleRepository = roleRepository;
    this.userRoleRepository = userRoleRepository;
    this.passwordHasherPort = passwordHasherPort;
    this.adminProperties = adminProperties;
    this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
    this.requiresNewTransactionTemplate.setPropagationBehavior(
        TransactionDefinition.PROPAGATION_REQUIRES_NEW);
  }

  @Override
  public void run(String... args) {
    String email = adminProperties.email();
    String password = adminProperties.password();

    if (!StringUtils.hasText(email) || !StringUtils.hasText(password)) {
      log.info("Admin account seed skipped: app.admin.email/password not configured");
      return;
    }

    String fullName =
        StringUtils.hasText(adminProperties.fullName())
            ? adminProperties.fullName()
            : "Administrator";

    try {
      requiresNewTransactionTemplate.executeWithoutResult(
          status -> seedAdmin(email, password, fullName));
    } catch (DataIntegrityViolationException exception) {
      log.info("Admin account seed skipped: {} already exists ({})", email, exception.getMessage());
    }
  }

  private void seedAdmin(String email, String password, String fullName) {
    Optional<UserEntity> existingUser = userRepository.findByEmailIgnoreCase(email);
    if (existingUser.isPresent()) {
      updateIfAlreadyAdmin(existingUser.get(), password, fullName);
      return;
    }

    UserEntity admin = new UserEntity();
    admin.setEmail(email);
    admin.setPasswordHash(passwordHasherPort.encode(password));
    admin.setFullName(fullName);
    admin.setStatus(UserStatus.ACTIVE);
    admin.setEmailVerifiedAt(OffsetDateTime.now());
    admin = userRepository.save(admin);

    RoleEntity adminRole = roleRepository.getReferenceById(ADMIN_ROLE_CODE);
    UserRoleEntity userRole = new UserRoleEntity();
    userRole.setUser(admin);
    userRole.setRole(adminRole);
    userRole.setCinemaId(null);
    userRoleRepository.save(userRole);

    log.info("Seeded admin account: {}", email);
  }

  private void updateIfAlreadyAdmin(UserEntity existingUser, String password, String fullName) {
    boolean isAlreadyAdmin =
        userRoleRepository.findRolesByUserId(existingUser.getId()).stream()
            .map(UserRoleAssignment::roleCode)
            .anyMatch(ADMIN_ROLE_CODE::equals);

    if (!isAlreadyAdmin) {
      log.warn(
          "Admin account seed skipped: a non-admin user already exists with email {}; refusing"
              + " to overwrite their credentials",
          existingUser.getEmail());
      return;
    }

    existingUser.setPasswordHash(passwordHasherPort.encode(password));
    existingUser.setFullName(fullName);
    userRepository.save(existingUser);
    log.info("Admin account updated: {}", existingUser.getEmail());
  }
}
