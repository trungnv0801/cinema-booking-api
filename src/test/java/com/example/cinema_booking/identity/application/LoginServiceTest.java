package com.example.cinema_booking.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.cinema_booking.identity.application.port.in.LoginCommand;
import com.example.cinema_booking.identity.application.port.in.LoginResult;
import com.example.cinema_booking.identity.application.port.out.IssuedTokens;
import com.example.cinema_booking.identity.application.port.out.LoadUserPort;
import com.example.cinema_booking.identity.application.port.out.LoadUserRolesPort;
import com.example.cinema_booking.identity.application.port.out.PasswordHasherPort;
import com.example.cinema_booking.identity.application.port.out.SaveRefreshTokenPort;
import com.example.cinema_booking.identity.application.port.out.TokenIssuerPort;
import com.example.cinema_booking.identity.application.port.out.UserRoleAssignment;
import com.example.cinema_booking.identity.domain.User;
import com.example.cinema_booking.identity.domain.UserStatus;
import com.example.cinema_booking.identity.domain.exception.AccountNotActiveException;
import com.example.cinema_booking.identity.domain.exception.InvalidCredentialsException;
import com.example.cinema_booking.identity.infrastructure.security.PasswordEncoderAdapter;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class LoginServiceTest {

  @Mock private LoadUserPort loadUserPort;
  @Mock private LoadUserRolesPort loadUserRolesPort;
  @Mock private PasswordHasherPort passwordHasherPort;
  @Mock private TokenIssuerPort tokenIssuerPort;
  @Mock private SaveRefreshTokenPort saveRefreshTokenPort;

  private LoginService loginService;

  @BeforeEach
  void setUp() {
    when(passwordHasherPort.encode(anyString())).thenReturn("dummy-password-hash");
    loginService =
        new LoginService(
            loadUserPort,
            loadUserRolesPort,
            passwordHasherPort,
            tokenIssuerPort,
            saveRefreshTokenPort);
  }

  private static User activeUser() {
    return User.builder()
        .id(1L)
        .publicId(UUID.randomUUID())
        .email("user@example.com")
        .passwordHash("hashed-password")
        .fullName("Jane Doe")
        .status(UserStatus.ACTIVE)
        .build();
  }

  @Test
  void loginReturnsTokensAndUserInfoOnSuccess() {
    User user = activeUser();
    LoginCommand command = new LoginCommand("user@example.com", "raw-password");
    OffsetDateTime refreshExpiry = OffsetDateTime.now().plusDays(30);
    IssuedTokens issuedTokens =
        new IssuedTokens("access-token", 900, "refresh-token", refreshExpiry);

    when(loadUserPort.loadByIdentifier("user@example.com")).thenReturn(Optional.of(user));
    when(passwordHasherPort.matches("raw-password", "hashed-password")).thenReturn(true);
    when(loadUserRolesPort.loadRoles(user.getId()))
        .thenReturn(List.of(new UserRoleAssignment("CUSTOMER", null)));
    when(tokenIssuerPort.issue(user, List.of(new UserRoleAssignment("CUSTOMER", null))))
        .thenReturn(issuedTokens);

    LoginResult result = loginService.login(command);

    assertThat(result.accessToken()).isEqualTo("access-token");
    assertThat(result.refreshToken()).isEqualTo("refresh-token");
    assertThat(result.tokenType()).isEqualTo("Bearer");
    assertThat(result.expiresInSeconds()).isEqualTo(900);
    assertThat(result.userPublicId()).isEqualTo(user.getPublicId());
    assertThat(result.fullName()).isEqualTo("Jane Doe");
    assertThat(result.roles()).containsExactly("CUSTOMER");

    verify(saveRefreshTokenPort).save(user.getId(), "refresh-token", refreshExpiry);
  }

  @Test
  void loginThrowsInvalidCredentialsWhenUserNotFound() {
    when(loadUserPort.loadByIdentifier("missing@example.com")).thenReturn(Optional.empty());

    assertThatThrownBy(
            () -> loginService.login(new LoginCommand("missing@example.com", "irrelevant")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(passwordHasherPort).matches(eq("irrelevant"), anyString());
    verifyNoInteractions(tokenIssuerPort, saveRefreshTokenPort);
  }

  @Test
  void loginThrowsInvalidCredentialsWhenPasswordHashIsNull() {
    User user = User.builder().id(1L).publicId(UUID.randomUUID()).status(UserStatus.ACTIVE).build();
    when(loadUserPort.loadByIdentifier("user@example.com")).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> loginService.login(new LoginCommand("user@example.com", "raw-password")))
        .isInstanceOf(InvalidCredentialsException.class);

    verify(passwordHasherPort).matches(eq("raw-password"), anyString());
  }

  @Test
  void loginThrowsInvalidCredentialsWhenPasswordDoesNotMatch() {
    User user = activeUser();
    when(loadUserPort.loadByIdentifier("user@example.com")).thenReturn(Optional.of(user));
    when(passwordHasherPort.matches("wrong-password", "hashed-password")).thenReturn(false);

    assertThatThrownBy(
            () -> loginService.login(new LoginCommand("user@example.com", "wrong-password")))
        .isInstanceOf(InvalidCredentialsException.class);

    verifyNoInteractions(tokenIssuerPort, saveRefreshTokenPort);
  }

  @Test
  void loginThrowsAccountNotActiveWhenUserIsLocked() {
    User user =
        User.builder()
            .id(1L)
            .publicId(UUID.randomUUID())
            .passwordHash("hashed-password")
            .status(UserStatus.LOCKED)
            .build();
    when(loadUserPort.loadByIdentifier("user@example.com")).thenReturn(Optional.of(user));
    when(passwordHasherPort.matches("raw-password", "hashed-password")).thenReturn(true);

    assertThatThrownBy(
            () -> loginService.login(new LoginCommand("user@example.com", "raw-password")))
        .isInstanceOf(AccountNotActiveException.class)
        .satisfies(
            ex ->
                assertThat(((AccountNotActiveException) ex).getArgs())
                    .containsExactly(UserStatus.LOCKED));

    verifyNoInteractions(tokenIssuerPort, saveRefreshTokenPort);
  }

  @Test
  void loginPassesResolvedRolesToTokenIssuer() {
    User user = activeUser();
    LoginCommand command = new LoginCommand("user@example.com", "raw-password");
    ArgumentCaptor<List<UserRoleAssignment>> rolesCaptor = ArgumentCaptor.forClass(List.class);
    UserRoleAssignment adminRole = new UserRoleAssignment("ADMIN", null);
    UserRoleAssignment cashierRole = new UserRoleAssignment("CASHIER", 5L);

    when(loadUserPort.loadByIdentifier("user@example.com")).thenReturn(Optional.of(user));
    when(passwordHasherPort.matches("raw-password", "hashed-password")).thenReturn(true);
    when(loadUserRolesPort.loadRoles(user.getId())).thenReturn(List.of(adminRole, cashierRole));
    when(tokenIssuerPort.issue(eq(user), rolesCaptor.capture()))
        .thenReturn(new IssuedTokens("a", 1, "r", OffsetDateTime.now()));

    LoginResult result = loginService.login(command);

    assertThat(rolesCaptor.getValue()).containsExactly(adminRole, cashierRole);
    assertThat(result.roles()).containsExactly("ADMIN", "CASHIER");
  }

  @Test
  void loginThrowsInvalidCredentialsInsteadOfCrashingOnOverLongPassword() {
    PasswordHasherPort realPasswordHasher = new PasswordEncoderAdapter(new BCryptPasswordEncoder());
    LoginService service =
        new LoginService(
            loadUserPort,
            loadUserRolesPort,
            realPasswordHasher,
            tokenIssuerPort,
            saveRefreshTokenPort);
    User user = activeUser();
    String eightyByteRawPassword = "a".repeat(80);
    when(loadUserPort.loadByIdentifier("user@example.com")).thenReturn(Optional.of(user));

    assertThatThrownBy(
            () -> service.login(new LoginCommand("user@example.com", eightyByteRawPassword)))
        .isInstanceOf(InvalidCredentialsException.class);

    verifyNoInteractions(tokenIssuerPort, saveRefreshTokenPort);
  }
}
