package com.example.cinema_booking.identity.infrastructure.security;

import com.example.cinema_booking.identity.application.port.out.IssuedTokens;
import com.example.cinema_booking.identity.application.port.out.TokenIssuerPort;
import com.example.cinema_booking.identity.application.port.out.UserRoleAssignment;
import com.example.cinema_booking.identity.domain.User;
import com.example.cinema_booking.shared.config.AppProperties;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenIssuerAdapter implements TokenIssuerPort {

  private final JwtService jwtService;
  private final Duration refreshTokenTtl;

  public JwtTokenIssuerAdapter(JwtService jwtService, AppProperties appProperties) {
    this.jwtService = jwtService;
    this.refreshTokenTtl = Duration.ofDays(appProperties.jwt().refreshTokenTtlDays());
  }

  @Override
  public IssuedTokens issue(User user, List<UserRoleAssignment> roles) {
    List<String> authorities = roles.stream().map(UserRoleAssignment::toAuthority).toList();
    String accessToken =
        jwtService.generateAccessToken(
            user.getPublicId().toString(),
            Map.of("email", user.getEmail() == null ? "" : user.getEmail(), "roles", authorities));

    String refreshToken = jwtService.generateOpaqueRefreshToken();
    OffsetDateTime refreshTokenExpiresAt = OffsetDateTime.now().plus(refreshTokenTtl);

    return new IssuedTokens(
        accessToken, jwtService.getAccessTokenTtlSeconds(), refreshToken, refreshTokenExpiresAt);
  }
}
