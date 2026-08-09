package com.example.cinema_booking.shared.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    OffsetDateTime timestamp,
    int status,
    String error,
    String message,
    Map<String, String> errors) {

  public ErrorResponse(OffsetDateTime timestamp, int status, String error, String message) {
    this(timestamp, status, error, message, null);
  }
}
