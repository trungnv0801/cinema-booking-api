package com.example.cinema_booking.identity.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class User {

  private final Long id;
  private final UUID publicId;
  private final String email;
  private final String phone;
  private final String passwordHash;
  private final String fullName;
  private final LocalDate dateOfBirth;
  private final UserStatus status;
  private final OffsetDateTime emailVerifiedAt;
  private final OffsetDateTime createdAt;
  private final OffsetDateTime updatedAt;
}
