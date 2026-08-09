package com.example.cinema_booking.shared.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

class SecurityErrorResponseWriterTest {

  private SecurityErrorResponseWriter writer;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasenames("messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);

    writer = new SecurityErrorResponseWriter(new ObjectMapper(), messageSource);
    request = new MockHttpServletRequest("GET", "/test");
    LocaleContextHolder.setLocale(Locale.ENGLISH);
  }

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void writesProblemDetailWithResolvedMessage() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(response, request, HttpStatus.UNAUTHORIZED, "error.unauthorized");

    assertThat(response.getStatus()).isEqualTo(401);
    assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

    Map<String, Object> body = readBody(response);
    assertThat(body.get("status")).isEqualTo(401);
    assertThat(body.get("title")).isEqualTo("Unauthorized");
    assertThat(body.get("detail")).isEqualTo("Authentication is required.");
    assertThat(body.get("type")).isEqualTo("/problems/unauthorized");
    assertThat(body.get("instance")).isEqualTo("/test");
    assertThat(body).containsKey("timestamp");
    assertThat(body).doesNotContainKey("errors");
  }

  @Test
  void fallsBackToMessageCodeWhenNoTranslationExists() throws Exception {
    MockHttpServletResponse response = new MockHttpServletResponse();

    writer.write(response, request, HttpStatus.FORBIDDEN, "unknown.message.code");

    Map<String, Object> body = readBody(response);
    assertThat(body.get("detail")).isEqualTo("unknown.message.code");
  }

  private static Map<String, Object> readBody(MockHttpServletResponse response) throws Exception {
    return new ObjectMapper()
        .readValue(response.getContentAsByteArray(), new TypeReference<Map<String, Object>>() {});
  }
}
