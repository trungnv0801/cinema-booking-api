package com.example.cinema_booking.shared.exception;

public abstract class DomainException extends RuntimeException {

  private final String messageCode;
  private final Object[] args;

  protected DomainException(String messageCode, Object... args) {
    this.messageCode = messageCode;
    this.args = args;
  }

  public String getMessageCode() {
    return messageCode;
  }

  public Object[] getArgs() {
    return args;
  }
}
