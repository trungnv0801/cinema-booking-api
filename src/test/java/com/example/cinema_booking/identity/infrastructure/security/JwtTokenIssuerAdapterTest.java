package com.example.cinema_booking.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.cinema_booking.identity.application.port.out.IssuedTokens;
import com.example.cinema_booking.identity.application.port.out.UserRoleAssignment;
import com.example.cinema_booking.identity.domain.User;
import com.example.cinema_booking.identity.domain.UserStatus;
import com.example.cinema_booking.shared.config.AppProperties;
import com.example.cinema_booking.shared.config.AppPropertiesTestFactory;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JwtTokenIssuerAdapterTest {

  @Mock private JwtService jwtService;

  private JwtTokenIssuerAdapter adapter;

  @BeforeEach
  void setUp() {
    AppProperties appProperties =
        AppPropertiesTestFactory.builder().jwt(new AppProperties.Jwt("secret", 900, 30)).build();
    adapter = new JwtTokenIssuerAdapter(jwtService, appProperties);
  }

  @Test
  void issueGeneratesAccessTokenWithSubjectAndClaims() {
    UUID publicId = UUID.randomUUID();
    User user =
        User.builder()
            .publicId(publicId)
            .email("user@example.com")
            .status(UserStatus.ACTIVE)
            .build();
    when(jwtService.generateAccessToken(eq(publicId.toString()), anyMap()))
        .thenReturn("access-token");
    when(jwtService.generateOpaqueRefreshToken()).thenReturn("refresh-token");
    when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

    IssuedTokens tokens = adapter.issue(user, List.of(new UserRoleAssignment("CUSTOMER", null)));

    ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jwtService).generateAccessToken(eq(publicId.toString()), claimsCaptor.capture());
    assertThat(claimsCaptor.getValue()).containsEntry("email", "user@example.com");
    assertThat(claimsCaptor.getValue()).containsEntry("roles", List.of("CUSTOMER"));

    assertThat(tokens.accessToken()).isEqualTo("access-token");
    assertThat(tokens.refreshToken()).isEqualTo("refresh-token");
    assertThat(tokens.accessTokenExpiresInSeconds()).isEqualTo(900L);
    assertThat(tokens.refreshTokenExpiresAt())
        .isCloseTo(OffsetDateTime.now().plus(Duration.ofDays(30)), within(5, ChronoUnit.SECONDS));
  }

  @Test
  void issueUsesEmptyStringWhenUserEmailIsNull() {
    User user = User.builder().publicId(UUID.randomUUID()).email(null).build();
    when(jwtService.generateAccessToken(eq(user.getPublicId().toString()), anyMap()))
        .thenReturn("access-token");
    when(jwtService.generateOpaqueRefreshToken()).thenReturn("refresh-token");

    adapter.issue(user, List.of());

    ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jwtService)
        .generateAccessToken(eq(user.getPublicId().toString()), claimsCaptor.capture());
    assertThat(claimsCaptor.getValue()).containsEntry("email", "");
  }

  @Test
  void issueEncodesCinemaScopedRolesAsDistinctAuthorities() {
    User user = User.builder().publicId(UUID.randomUUID()).email("cashier@example.com").build();
    when(jwtService.generateAccessToken(eq(user.getPublicId().toString()), anyMap()))
        .thenReturn("access-token");
    when(jwtService.generateOpaqueRefreshToken()).thenReturn("refresh-token");

    adapter.issue(
        user,
        List.of(new UserRoleAssignment("CASHIER", 5L), new UserRoleAssignment("CASHIER", 7L)));

    ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(jwtService)
        .generateAccessToken(eq(user.getPublicId().toString()), claimsCaptor.capture());
    assertThat(claimsCaptor.getValue())
        .containsEntry("roles", List.of("CASHIER_CINEMA_5", "CASHIER_CINEMA_7"));
  }
}
