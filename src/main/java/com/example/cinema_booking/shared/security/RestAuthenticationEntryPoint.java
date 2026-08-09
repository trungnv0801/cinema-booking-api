package com.example.cinema_booking.shared.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final SecurityErrorResponseWriter errorResponseWriter;

  public RestAuthenticationEntryPoint(SecurityErrorResponseWriter errorResponseWriter) {
    this.errorResponseWriter = errorResponseWriter;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException, ServletException {
    String messageCode =
        authException instanceof BadCredentialsException
            ? "auth.invalid-credentials"
            : "error.unauthorized";
    errorResponseWriter.write(response, request, HttpStatus.UNAUTHORIZED, messageCode);
  }
}
