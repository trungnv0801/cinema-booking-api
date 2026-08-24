package com.example.cinema_booking.identity.application.port.in;

import java.time.LocalDate;

public record RegisterCommand(
    String fullName, String email, String phone, String rawPassword, LocalDate dateOfBirth) {}
