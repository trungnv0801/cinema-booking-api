package com.example.cinema_booking.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;

class Sha256RefreshTokenHasherTest {

  private final Sha256RefreshTokenHasher hasher = new Sha256RefreshTokenHasher();

  @Test
  void hashIsDeterministicForTheSameInput() {
    assertThat(hasher.hash("same-token")).isEqualTo(hasher.hash("same-token"));
  }

  @Test
  void hashDiffersForDifferentInput() {
    assertThat(hasher.hash("token-a")).isNotEqualTo(hasher.hash("token-b"));
  }

  @Test
  void hashNeverReturnsTheRawToken() {
    assertThat(hasher.hash("raw-refresh-token")).isNotEqualTo("raw-refresh-token");
  }

  @Test
  void hashDoesNotThrowForTokensLongerThanBcryptsSeventyTwoByteLimit() {
    String eightySixCharToken = "a".repeat(86);

    assertThatCode(() -> hasher.hash(eightySixCharToken)).doesNotThrowAnyException();
  }
}
