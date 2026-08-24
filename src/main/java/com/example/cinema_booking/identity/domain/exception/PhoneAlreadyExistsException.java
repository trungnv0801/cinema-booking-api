package com.example.cinema_booking.identity.domain.exception;

import com.example.cinema_booking.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class PhoneAlreadyExistsException extends DomainException {

  public PhoneAlreadyExistsException() {
    super(HttpStatus.CONFLICT, "auth.phone-already-exists");
  }
}
