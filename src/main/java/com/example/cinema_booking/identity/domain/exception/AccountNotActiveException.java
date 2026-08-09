package com.example.cinema_booking.identity.domain.exception;

import com.example.cinema_booking.identity.domain.UserStatus;
import com.example.cinema_booking.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

public class AccountNotActiveException extends DomainException {

  public AccountNotActiveException(UserStatus status) {
    super(HttpStatus.FORBIDDEN, "auth.account-not-active", status);
  }
}
