package com.example.cinema_booking.identity.infrastructure.persistence.adapter;

import com.example.cinema_booking.identity.application.port.out.RefreshTokenHasherPort;
import com.example.cinema_booking.identity.application.port.out.SaveRefreshTokenPort;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.RefreshTokenEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.entity.UserEntity;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.RefreshTokenRepository;
import com.example.cinema_booking.identity.infrastructure.persistence.repository.UserRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class RefreshTokenPersistenceAdapter implements SaveRefreshTokenPort {

  private final RefreshTokenRepository refreshTokenRepository;
  private final UserRepository userRepository;
  private final RefreshTokenHasherPort refreshTokenHasherPort;

  public RefreshTokenPersistenceAdapter(
      RefreshTokenRepository refreshTokenRepository,
      UserRepository userRepository,
      RefreshTokenHasherPort refreshTokenHasherPort) {
    this.refreshTokenRepository = refreshTokenRepository;
    this.userRepository = userRepository;
    this.refreshTokenHasherPort = refreshTokenHasherPort;
  }

  @Override
  public void save(Long userId, String rawRefreshToken, OffsetDateTime expiresAt) {
    UserEntity user = userRepository.getReferenceById(userId);

    RefreshTokenEntity refreshToken = new RefreshTokenEntity();
    refreshToken.setUser(user);
    refreshToken.setTokenHash(refreshTokenHasherPort.hash(rawRefreshToken));
    refreshToken.setExpiresAt(expiresAt);
    refreshTokenRepository.save(refreshToken);
  }
}
