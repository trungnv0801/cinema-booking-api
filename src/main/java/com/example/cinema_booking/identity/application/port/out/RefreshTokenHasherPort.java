package com.example.cinema_booking.identity.application.port.out;

public interface RefreshTokenHasherPort {

  String hash(String rawToken);
}
