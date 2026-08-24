package com.example.cinema_booking.identity.api.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegisterResponse(User user) {

  public record User(
      UUID id,
      String fullName,
      String email,
      String phone,
      LocalDate dateOfBirth,
      List<String> roles) {}
}
