package com.example.cinema_booking.identity.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class PasswordEncoderAdapterTest {

  @Mock private PasswordEncoder passwordEncoder;

  private PasswordEncoderAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new PasswordEncoderAdapter(passwordEncoder);
  }

  @Test
  void encodeDelegatesToPasswordEncoder() {
    when(passwordEncoder.encode("raw-password")).thenReturn("encoded-hash");

    assertThat(adapter.encode("raw-password")).isEqualTo("encoded-hash");
  }

  @Test
  void matchesReturnsTrueWhenPasswordEncoderConfirmsMatch() {
    when(passwordEncoder.matches("raw-password", "hashed-password")).thenReturn(true);

    assertThat(adapter.matches("raw-password", "hashed-password")).isTrue();
  }

  @Test
  void matchesReturnsFalseWhenPasswordEncoderRejectsMatch() {
    when(passwordEncoder.matches("wrong-password", "hashed-password")).thenReturn(false);

    assertThat(adapter.matches("wrong-password", "hashed-password")).isFalse();
  }

  @Test
  void matchesReturnsFalseInsteadOfThrowingWhenEncoderRejectsOverLongInput() {
    when(passwordEncoder.matches("too-long-password", "hashed-password"))
        .thenThrow(new IllegalArgumentException("password cannot be more than 72 bytes"));

    assertThatCode(() -> adapter.matches("too-long-password", "hashed-password"))
        .doesNotThrowAnyException();
    assertThat(adapter.matches("too-long-password", "hashed-password")).isFalse();
  }

  @Test
  void matchesReturnsFalseForRealBcryptEncoderGivenPasswordOverSeventyTwoBytes() {
    PasswordEncoderAdapter realAdapter = new PasswordEncoderAdapter(new BCryptPasswordEncoder());
    String hash = new BCryptPasswordEncoder().encode("normal-password");
    String eightyByteRawPassword = "a".repeat(80);

    assertThatCode(() -> realAdapter.matches(eightyByteRawPassword, hash))
        .doesNotThrowAnyException();
    assertThat(realAdapter.matches(eightyByteRawPassword, hash)).isFalse();
  }
}
