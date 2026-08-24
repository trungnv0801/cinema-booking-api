package com.example.cinema_booking.identity.domain.exception;

import com.example.cinema_booking.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class EmailAlreadyExistsException extends DomainException {

  public EmailAlreadyExistsException() {
    super(HttpStatus.CONFLICT, "auth.email-already-exists");
  }
}
