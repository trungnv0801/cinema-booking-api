package com.example.cinema_booking.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.time.OffsetDateTime;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

public final class ProblemDetailFactory {

  private static final String PROBLEM_TYPE_BASE = "/problems/";

  private ProblemDetailFactory() {}

  public static ProblemDetail create(
      HttpStatus status, String messageCode, String detail, HttpServletRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setType(URI.create(PROBLEM_TYPE_BASE + problemTypeSlug(messageCode)));
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    problemDetail.setProperty("timestamp", OffsetDateTime.now());
    return problemDetail;
  }

  private static String problemTypeSlug(String messageCode) {
    int dotIndex = messageCode.indexOf('.');
    return dotIndex >= 0 ? messageCode.substring(dotIndex + 1) : messageCode;
  }
}
