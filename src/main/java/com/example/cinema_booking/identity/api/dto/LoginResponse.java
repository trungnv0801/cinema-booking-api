package com.example.cinema_booking.identity.api.dto;

import java.util.List;
import java.util.UUID;

public record LoginResponse(String accessToken, String tokenType, long expiresIn, User user) {

  public record User(UUID id, String fullName, List<String> roles) {}
}
