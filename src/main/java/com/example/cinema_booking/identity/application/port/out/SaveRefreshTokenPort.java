package com.example.cinema_booking.identity.application.port.out;

import java.time.OffsetDateTime;

public interface SaveRefreshTokenPort {

  void save(Long userId, String rawRefreshToken, OffsetDateTime expiresAt);
}
