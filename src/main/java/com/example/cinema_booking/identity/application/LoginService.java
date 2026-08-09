package com.example.cinema_booking.identity.application;

import com.example.cinema_booking.identity.application.port.in.LoginCommand;
import com.example.cinema_booking.identity.application.port.in.LoginResult;
import com.example.cinema_booking.identity.application.port.in.LoginUseCase;
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
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class LoginService implements LoginUseCase {

  private final String dummyPasswordHash;
  private final LoadUserPort loadUserPort;
  private final LoadUserRolesPort loadUserRolesPort;
  private final PasswordHasherPort passwordHasherPort;
  private final TokenIssuerPort tokenIssuerPort;
  private final SaveRefreshTokenPort saveRefreshTokenPort;

  public LoginService(
      LoadUserPort loadUserPort,
      LoadUserRolesPort loadUserRolesPort,
      PasswordHasherPort passwordHasherPort,
      TokenIssuerPort tokenIssuerPort,
      SaveRefreshTokenPort saveRefreshTokenPort) {
    this.loadUserPort = loadUserPort;
    this.loadUserRolesPort = loadUserRolesPort;
    this.passwordHasherPort = passwordHasherPort;
    this.tokenIssuerPort = tokenIssuerPort;
    this.saveRefreshTokenPort = saveRefreshTokenPort;
    this.dummyPasswordHash =
        passwordHasherPort.encode("dummy-password-for-constant-time-comparison");
  }

  @Override
  public LoginResult login(LoginCommand command) {
    Optional<User> maybeUser = loadUserPort.loadByIdentifier(command.identifier());
    String passwordHash = maybeUser.map(User::getPasswordHash).orElse(dummyPasswordHash);
    boolean passwordMatches = passwordHasherPort.matches(command.rawPassword(), passwordHash);

    if (maybeUser.isEmpty() || !passwordMatches) {
      throw new InvalidCredentialsException();
    }
    User user = maybeUser.get();
    if (user.getStatus() != UserStatus.ACTIVE) {
      throw new AccountNotActiveException(user.getStatus());
    }

    List<UserRoleAssignment> roles = loadUserRolesPort.loadRoles(user.getId());
    IssuedTokens tokens = tokenIssuerPort.issue(user, roles);
    saveRefreshTokenPort.save(user.getId(), tokens.refreshToken(), tokens.refreshTokenExpiresAt());

    List<String> roleCodes = roles.stream().map(UserRoleAssignment::roleCode).distinct().toList();

    return new LoginResult(
        tokens.accessToken(),
        tokens.refreshToken(),
        "Bearer",
        tokens.accessTokenExpiresInSeconds(),
        user.getPublicId(),
        user.getFullName(),
        roleCodes);
  }
}
