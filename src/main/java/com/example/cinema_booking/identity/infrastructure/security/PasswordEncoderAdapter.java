package com.example.cinema_booking.identity.infrastructure.security;

import com.example.cinema_booking.identity.application.port.out.PasswordHasherPort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class PasswordEncoderAdapter implements PasswordHasherPort {

  private final PasswordEncoder passwordEncoder;

  public PasswordEncoderAdapter(PasswordEncoder passwordEncoder) {
    this.passwordEncoder = passwordEncoder;
  }

  @Override
  public boolean matches(String rawPassword, String hashedPassword) {
    try {
      return passwordEncoder.matches(rawPassword, hashedPassword);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }

  @Override
  public String encode(String rawPassword) {
    return passwordEncoder.encode(rawPassword);
  }
}
