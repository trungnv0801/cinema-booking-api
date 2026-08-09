package com.example.cinema_booking.shared.exception;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.NotBlank;
import java.lang.reflect.Method;
import java.net.URI;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

class GlobalExceptionHandlerTest {

  private GlobalExceptionHandler handler;
  private MockHttpServletRequest request;

  @BeforeEach
  void setUp() {
    ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
    messageSource.setBasenames("messages");
    messageSource.setDefaultEncoding("UTF-8");
    messageSource.setFallbackToSystemLocale(false);

    handler = new GlobalExceptionHandler(messageSource);
    request = new MockHttpServletRequest("GET", "/test");
    LocaleContextHolder.setLocale(Locale.ENGLISH);
  }

  @AfterEach
  void tearDown() {
    LocaleContextHolder.resetLocaleContext();
  }

  @Test
  void handlesMethodArgumentNotValidWithFieldErrors() throws Exception {
    BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "loginRequest");
    bindingResult.addError(new FieldError("loginRequest", "identifier", "must not be blank"));
    MethodParameter methodParameter = dummyMethodParameter();

    ProblemDetail problemDetail =
        handler.handleMethodArgumentNotValid(
            new MethodArgumentNotValidException(methodParameter, bindingResult), request);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problemDetail.getType()).isEqualTo(URI.create("/problems/validation-failed"));
    assertThat(problemDetail.getInstance()).isEqualTo(URI.create("/test"));
    assertThat(problemDetail.getDetail()).isEqualTo("The submitted data is invalid.");
    assertThat(fieldErrors(problemDetail)).containsEntry("identifier", "must not be blank");
  }

  @Test
  void handlesConstraintViolationWithFieldErrors() {
    Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
    Set<ConstraintViolation<Validatable>> violations = validator.validate(new Validatable(""));

    ProblemDetail problemDetail =
        handler.handleConstraintViolation(new ConstraintViolationException(violations), request);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(fieldErrors(problemDetail)).containsKey("value");
  }

  @Test
  void handlesMessageNotReadable() {
    ProblemDetail problemDetail =
        handler.handleMessageNotReadable(
            new HttpMessageNotReadableException(
                "broken", (org.springframework.http.HttpInputMessage) null),
            request);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problemDetail.getDetail()).isEqualTo("The request is malformed or invalid.");
  }

  @Test
  void handlesMissingParameter() {
    ProblemDetail problemDetail =
        handler.handleMissingParameter(
            new MissingServletRequestParameterException("identifier", "String"), request);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problemDetail.getDetail()).isEqualTo("Required parameter 'identifier' is missing.");
  }

  @Test
  void handlesTypeMismatch() throws Exception {
    MethodArgumentTypeMismatchException ex =
        new MethodArgumentTypeMismatchException(
            "abc", Long.class, "id", dummyMethodParameter(), null);

    ProblemDetail problemDetail = handler.handleTypeMismatch(ex, request);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
    assertThat(problemDetail.getDetail()).isEqualTo("Parameter 'id' has an invalid value.");
  }

  @Test
  void handlesMethodNotSupported() {
    ProblemDetail problemDetail =
        handler.handleMethodNotSupported(
            new HttpRequestMethodNotSupportedException("PUT"), request);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
    assertThat(problemDetail.getDetail())
        .isEqualTo("Request method PUT is not supported for this endpoint.");
  }

  @Test
  void handlesMediaTypeNotSupported() {
    ProblemDetail problemDetail =
        handler.handleMediaTypeNotSupported(
            new HttpMediaTypeNotSupportedException("text/plain"), request);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
    assertThat(problemDetail.getDetail()).isEqualTo("The request content type is not supported.");
  }

  @Test
  void handlesNoResourceFound() {
    ProblemDetail problemDetail =
        handler.handleNoResourceFound(
            new NoResourceFoundException(HttpMethod.GET, "/missing", "not found"), request);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
    assertThat(problemDetail.getDetail()).isEqualTo("The requested resource was not found.");
  }

  @Test
  void handlesDataIntegrityViolation() {
    ProblemDetail problemDetail =
        handler.handleDataIntegrityViolation(
            new DataIntegrityViolationException("dup key"), request);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
    assertThat(problemDetail.getDetail()).isEqualTo("The data conflicts with existing data.");
  }

  @Test
  void handlesUnexpectedException() {
    ProblemDetail problemDetail = handler.handleUnexpected(new RuntimeException("boom"), request);

    assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
    assertThat(problemDetail.getDetail())
        .isEqualTo("An unexpected error occurred. Please try again later.");
  }

  @SuppressWarnings("unchecked")
  private static Map<String, String> fieldErrors(ProblemDetail problemDetail) {
    return (Map<String, String>) problemDetail.getProperties().get("errors");
  }

  private static MethodParameter dummyMethodParameter() throws NoSuchMethodException {
    Method method = GlobalExceptionHandlerTest.class.getDeclaredMethod("dummyMethod", String.class);
    return new MethodParameter(method, 0);
  }

  @SuppressWarnings("unused")
  private void dummyMethod(String value) {}

  private record Validatable(@NotBlank String value) {}
}
