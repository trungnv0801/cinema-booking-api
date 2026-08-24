package com.example.cinema_booking.identity.application.port.in;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RegisterResult(
    UUID publicId,
    String fullName,
    String email,
    String phone,
    LocalDate dateOfBirth,
    List<String> roles) {}
