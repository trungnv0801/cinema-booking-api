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
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class RestAccessDeniedHandlerTest {

  @Mock private SecurityErrorResponseWriter errorResponseWriter;

  private RestAccessDeniedHandler accessDeniedHandler;

  @BeforeEach
  void setUp() {
    accessDeniedHandler = new RestAccessDeniedHandler(errorResponseWriter);
  }

  @Test
  void handleWritesForbiddenErrorResponse() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();

    accessDeniedHandler.handle(request, response, new AccessDeniedException("denied"));

    verify(errorResponseWriter)
        .write(response, request, HttpStatus.FORBIDDEN, "error.access-denied");
  }
}
