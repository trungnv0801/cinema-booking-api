package com.example.cinema_booking.identity.application.port.out;

public interface PasswordHasherPort {

  boolean matches(String rawPassword, String hashedPassword);

  String encode(String rawPassword);
}
