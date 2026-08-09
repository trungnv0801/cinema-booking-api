package com.example.cinema_booking.identity.api.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank(message = "{validation.identifier.required}") String identifier,
    @NotBlank(message = "{validation.password.required}") String password) {}
