package com.example.cinema_booking.identity.application.port.in;

public interface LoginUseCase {

  LoginResult login(LoginCommand command);
}
