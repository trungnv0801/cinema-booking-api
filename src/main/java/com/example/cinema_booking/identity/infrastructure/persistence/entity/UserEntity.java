package com.example.cinema_booking.identity.infrastructure.persistence.entity;

import com.example.cinema_booking.identity.domain.UserStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UserEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "public_id", nullable = false, updatable = false)
  private UUID publicId;

  @Column(length = 255)
  private String email;

  @Column(length = 20)
  private String phone;

  @Column(name = "password_hash", length = 255)
  private String passwordHash;

  @Column(name = "full_name", nullable = false, length = 255)
  private String fullName;

  @Column(name = "date_of_birth")
  private LocalDate dateOfBirth;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserStatus status = UserStatus.ACTIVE;

  @Column(name = "email_verified_at")
  private OffsetDateTime emailVerifiedAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  private OffsetDateTime createdAt;

  @Column(name = "updated_at", nullable = false)
  private OffsetDateTime updatedAt;

  @PrePersist
  private void onCreate() {
    if (publicId == null) {
      publicId = UUID.randomUUID();
    }
    OffsetDateTime now = OffsetDateTime.now();
    createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  private void onUpdate() {
    updatedAt = OffsetDateTime.now();
  }
}
