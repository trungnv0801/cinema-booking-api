package com.example.cinema_booking.shared.security;

import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InsufficientAuthenticationException;

@ExtendWith(MockitoExtension.class)
class RestAuthenticationEntryPointTest {

  @Mock private SecurityErrorResponseWriter errorResponseWriter;

  private RestAuthenticationEntryPoint entryPoint;

  @BeforeEach
  void setUp() {
    entryPoint = new RestAuthenticationEntryPoint(errorResponseWriter);
  }

  @Test
  void commenceWritesInvalidCredentialsErrorResponseForBadCredentials() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    entryPoint.commence(request, response, new BadCredentialsException("bad credentials"));

    verify(errorResponseWriter)
        .write(response, request, HttpStatus.UNAUTHORIZED, "auth.invalid-credentials");
  }

  @Test
  void commenceWritesUnauthorizedErrorResponseForOtherAuthenticationFailures() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    entryPoint.commence(
        request, response, new InsufficientAuthenticationException("missing token"));

    verify(errorResponseWriter)
        .write(response, request, HttpStatus.UNAUTHORIZED, "error.unauthorized");
  }
}
