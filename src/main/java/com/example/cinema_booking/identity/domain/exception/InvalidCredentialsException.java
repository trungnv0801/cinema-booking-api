package com.example.cinema_booking.identity.domain.exception;

import com.example.cinema_booking.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class InvalidCredentialsException extends DomainException {

  public InvalidCredentialsException() {
    super(HttpStatus.UNAUTHORIZED, "auth.invalid-credentials");
  }
}
