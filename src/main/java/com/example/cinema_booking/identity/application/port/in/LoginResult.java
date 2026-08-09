package com.example.cinema_booking.identity.application.port.in;

import java.util.List;
import java.util.UUID;

public record LoginResult(
    String accessToken,
    String refreshToken,
    String tokenType,
    long expiresInSeconds,
    UUID userPublicId,
    String fullName,
    List<String> roles) {}
