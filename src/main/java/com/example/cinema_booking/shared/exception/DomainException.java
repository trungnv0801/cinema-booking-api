package com.example.cinema_booking.shared.exception;

import org.springframework.http.HttpStatus;

public abstract class DomainException extends RuntimeException {

  private final HttpStatus status;
  private final String messageCode;
  private final Object[] args;

  protected DomainException(HttpStatus status, String messageCode, Object... args) {
    this.status = status;
    this.messageCode = messageCode;
    this.args = args;
  }

  public HttpStatus getStatus() {
    return status;
  }

  public String getMessageCode() {
    return messageCode;
  }

  public Object[] getArgs() {
    return args;
  }
}
