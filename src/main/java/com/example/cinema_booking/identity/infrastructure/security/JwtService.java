package com.example.cinema_booking.identity.infrastructure.security;

import com.example.cinema_booking.shared.config.AppProperties;
import com.example.cinema_booking.shared.security.TokenAuthenticationPort;
import com.example.cinema_booking.shared.security.TokenPrincipal;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.InvalidKeyException;
import io.jsonwebtoken.security.Keys;
import java.security.Key;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtService implements TokenAuthenticationPort {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final Key signingKey;
  private final long accessTokenTtlSeconds;

  public JwtService(AppProperties appProperties) {
    this.signingKey = buildSigningKey(appProperties.jwt().secret());
    this.accessTokenTtlSeconds = appProperties.jwt().accessTokenTtlSeconds();
  }

  private static Key buildSigningKey(String secret) {
    if (!StringUtils.hasText(secret)) {
      throw new IllegalStateException(
          "app.jwt.secret (JWT_SECRET) is not configured. Set it to a Base64-encoded value of"
              + " at least 32 bytes, e.g. `openssl rand -base64 48`.");
    }
    byte[] keyBytes;
    try {
      keyBytes = Decoders.BASE64.decode(secret);
    } catch (DecodingException e) {
      throw new IllegalStateException(
          "app.jwt.secret (JWT_SECRET) must be valid Base64-encoded text.", e);
    }
    try {
      return Keys.hmacShaKeyFor(keyBytes);
    } catch (InvalidKeyException e) {
      throw new IllegalStateException(
          "app.jwt.secret (JWT_SECRET) decodes to "
              + keyBytes.length
              + " bytes, but at least 32 bytes (256 bits) are required for HMAC-SHA signing.",
          e);
    }
  }

  public long getAccessTokenTtlSeconds() {
    return accessTokenTtlSeconds;
  }

  public String generateAccessToken(String subject, Map<String, Object> claims) {
    Date now = new Date();
    Date expiry = new Date(now.getTime() + accessTokenTtlSeconds * 1000);
    return Jwts.builder()
        .setClaims(claims)
        .setSubject(subject)
        .setIssuedAt(now)
        .setExpiration(expiry)
        .signWith(signingKey, SignatureAlgorithm.HS256)
        .compact();
  }

  public String generateOpaqueRefreshToken() {
    byte[] bytes = new byte[64];
    SECURE_RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  @Override
  @SuppressWarnings("unchecked")
  public Optional<TokenPrincipal> authenticate(String token) {
    try {
      Claims claims = extractAllClaims(token);
      List<String> roles = (List<String>) claims.get("roles");
      return Optional.of(
          new TokenPrincipal(claims.getSubject(), roles == null ? List.of() : roles));
    } catch (JwtException | IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  private Claims extractAllClaims(String token) {
    return Jwts.parserBuilder().setSigningKey(signingKey).build().parseClaimsJws(token).getBody();
  }
}
