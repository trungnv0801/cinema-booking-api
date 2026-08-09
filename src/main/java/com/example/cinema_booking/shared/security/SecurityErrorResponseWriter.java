package com.example.cinema_booking.shared.security;

import com.example.cinema_booking.shared.exception.ProblemDetailFactory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class SecurityErrorResponseWriter {

  private final ObjectMapper objectMapper;
  private final MessageSource messageSource;

  public SecurityErrorResponseWriter(ObjectMapper objectMapper, MessageSource messageSource) {
    this.objectMapper =
        objectMapper
            .rebuild()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .build();
    this.messageSource = messageSource;
  }

  public void write(
      HttpServletResponse response,
      HttpServletRequest request,
      HttpStatus status,
      String messageCode)
      throws IOException {
    String detail =
        messageSource.getMessage(messageCode, null, messageCode, LocaleContextHolder.getLocale());

    ProblemDetail problemDetail = ProblemDetailFactory.create(status, messageCode, detail, request);

    response.setStatus(status.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), problemDetail);
  }
}
