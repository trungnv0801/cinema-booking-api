package com.example.cinema_booking.identity.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnTransformer;

@Entity
@Table(name = "refresh_tokens")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RefreshTokenEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @Column(name = "token_hash", nullable = false, length = 255)
  private String tokenHash;

  @Column(name = "issued_at", nullable = false, updatable = false)
  private OffsetDateTime issuedAt;

  @Column(name = "expires_at", nullable = false)
  private OffsetDateTime expiresAt;

  @Column(name = "revoked_at")
  private OffsetDateTime revokedAt;

  @Column(name = "user_agent", length = 255)
  private String userAgent;

  // Explicit cast in the generated SQL: Postgres won't implicitly cast a
  // varchar-bound JDBC parameter to inet, so the write side casts it directly.
  @ColumnTransformer(write = "?::inet")
  @Column(name = "ip_address", columnDefinition = "inet")
  private String ipAddress;

  @PrePersist
  private void onCreate() {
    if (issuedAt == null) {
      issuedAt = OffsetDateTime.now();
    }
  }
}
