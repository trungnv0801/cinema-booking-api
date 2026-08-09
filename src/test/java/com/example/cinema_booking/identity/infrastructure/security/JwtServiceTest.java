package com.example.cinema_booking.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.cinema_booking.shared.config.AppProperties;
import com.example.cinema_booking.shared.config.AppPropertiesTestFactory;
import com.example.cinema_booking.shared.security.TokenPrincipal;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

  private static final String SECRET = Base64.getEncoder().encodeToString(new byte[32]);

  private JwtService jwtService;

  @BeforeEach
  void setUp() {
    AppProperties appProperties =
        AppPropertiesTestFactory.builder().jwt(new AppProperties.Jwt(SECRET, 900, 30)).build();
    jwtService = new JwtService(appProperties);
  }

  @Test
  void getAccessTokenTtlSecondsReturnsConfiguredValue() {
    assertThat(jwtService.getAccessTokenTtlSeconds()).isEqualTo(900);
  }

  @Test
  void constructorThrowsClearConfigErrorWhenSecretIsBlank() {
    AppProperties appProperties =
        AppPropertiesTestFactory.builder().jwt(new AppProperties.Jwt(" ", 900, 30)).build();

    assertThatThrownBy(() -> new JwtService(appProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET")
        .hasMessageContaining("not configured");
  }

  @Test
  void constructorThrowsClearConfigErrorWhenSecretIsNull() {
    AppProperties appProperties =
        AppPropertiesTestFactory.builder().jwt(new AppProperties.Jwt(null, 900, 30)).build();

    assertThatThrownBy(() -> new JwtService(appProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET");
  }

  @Test
  void constructorThrowsClearConfigErrorWhenSecretIsNotValidBase64() {
    AppProperties appProperties =
        AppPropertiesTestFactory.builder()
            .jwt(new AppProperties.Jwt("not-valid-base64!!!", 900, 30))
            .build();

    assertThatThrownBy(() -> new JwtService(appProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET")
        .hasMessageContaining("Base64");
  }

  @Test
  void constructorThrowsClearConfigErrorWhenSecretDecodesToFewerThanThirtyTwoBytes() {
    String tooShortSecret = Base64.getEncoder().encodeToString(new byte[8]);
    AppProperties appProperties =
        AppPropertiesTestFactory.builder()
            .jwt(new AppProperties.Jwt(tooShortSecret, 900, 30))
            .build();

    assertThatThrownBy(() -> new JwtService(appProperties))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT_SECRET")
        .hasMessageContaining("32 bytes");
  }

  @Test
  void reusesASingleSecureRandomInstanceInsteadOfCreatingOnePerCall() throws Exception {
    Field field = JwtService.class.getDeclaredField("SECURE_RANDOM");

    assertThat(Modifier.isStatic(field.getModifiers())).isTrue();
    assertThat(Modifier.isFinal(field.getModifiers())).isTrue();
    assertThat(field.getType()).isEqualTo(SecureRandom.class);
  }

  @Test
  void authenticateReturnsSubjectAndRolesForValidToken() {
    String token =
        jwtService.generateAccessToken("user-public-id", Map.of("roles", List.of("CUSTOMER")));

    assertThat(jwtService.authenticate(token))
        .contains(new TokenPrincipal("user-public-id", List.of("CUSTOMER")));
  }

  @Test
  void authenticateReturnsEmptyRolesWhenClaimIsMissing() {
    String token = jwtService.generateAccessToken("user-public-id", Map.of());

    assertThat(jwtService.authenticate(token))
        .contains(new TokenPrincipal("user-public-id", List.of()));
  }

  @Test
  void authenticateReturnsEmptyOptionalForMalformedToken() {
    assertThat(jwtService.authenticate("not-a-jwt")).isEmpty();
  }

  @Test
  void authenticateReturnsEmptyOptionalForExpiredToken() {
    Date issuedAt = new Date(System.currentTimeMillis() - 60_000);
    Date expiry = new Date(System.currentTimeMillis() - 1_000);
    String expiredToken =
        Jwts.builder()
            .setSubject("user-public-id")
            .setIssuedAt(issuedAt)
            .setExpiration(expiry)
            .signWith(Keys.hmacShaKeyFor(Decoders.BASE64.decode(SECRET)), SignatureAlgorithm.HS256)
            .compact();

    assertThat(jwtService.authenticate(expiredToken)).isEmpty();
  }

  @Test
  void authenticateReturnsEmptyOptionalForTokenSignedWithDifferentKey() {
    String otherSecret =
        Base64.getEncoder()
            .encodeToString(
                new byte[] {
                  1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16,
                  17, 18, 19, 20, 21, 22, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32
                });
    String tokenSignedByOtherKey =
        Jwts.builder()
            .setSubject("user-public-id")
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + 60_000))
            .signWith(
                Keys.hmacShaKeyFor(Decoders.BASE64.decode(otherSecret)), SignatureAlgorithm.HS256)
            .compact();

    assertThat(jwtService.authenticate(tokenSignedByOtherKey)).isEmpty();
  }

  @Test
  void authenticateReturnsEmptyOptionalForTamperedToken() {
    String token =
        jwtService.generateAccessToken("user-public-id", Map.of("roles", List.of("ADMIN")));
    int payloadStart = token.indexOf('.') + 1;
    String tamperedToken =
        token.substring(0, payloadStart)
            + (token.charAt(payloadStart) == 'a' ? 'b' : 'a')
            + token.substring(payloadStart + 1);

    assertThat(jwtService.authenticate(tamperedToken)).isEmpty();
  }

  @Test
  void generateOpaqueRefreshTokenReturnsUniqueUrlSafeValues() {
    String first = jwtService.generateOpaqueRefreshToken();
    String second = jwtService.generateOpaqueRefreshToken();

    assertThat(first).isNotEqualTo(second);
    assertThat(first).doesNotContain("+", "/", "=");
  }
}
