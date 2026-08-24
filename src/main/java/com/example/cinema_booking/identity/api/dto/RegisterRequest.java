package com.example.cinema_booking.identity.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record RegisterRequest(
    @NotBlank(message = "{validation.full-name.required}") String fullName,
    @NotBlank(message = "{validation.email.required}")
        @Email(message = "{validation.email.invalid}")
        String email,
    @Pattern(regexp = "\\+?[0-9]{8,15}", message = "{validation.phone.invalid}") String phone,
    @NotBlank(message = "{validation.password.required}")
        @Size(min = 8, max = 72, message = "{validation.password.size}")
        String password,
    @Past(message = "{validation.date-of-birth.past}") LocalDate dateOfBirth) {}
