package com.example.cinema_booking.identity.application.port.out;

import java.time.OffsetDateTime;

public record IssuedTokens(
    String accessToken,
    long accessTokenExpiresInSeconds,
    String refreshToken,
    OffsetDateTime refreshTokenExpiresAt) {}
