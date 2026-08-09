package com.example.cinema_booking.shared.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final String PROBLEM_TYPE_BASE = "/problems/";

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final MessageSource messageSource;

  public GlobalExceptionHandler(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex, HttpServletRequest request) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    return build(HttpStatus.BAD_REQUEST, "error.validation-failed", request, fieldErrors);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ProblemDetail handleConstraintViolation(
      ConstraintViolationException ex, HttpServletRequest request) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      fieldErrors.put(violation.getPropertyPath().toString(), violation.getMessage());
    }
    return build(HttpStatus.BAD_REQUEST, "error.validation-failed", request, fieldErrors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ProblemDetail handleMessageNotReadable(
      HttpMessageNotReadableException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "error.malformed-request", request);
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ProblemDetail handleMissingParameter(
      MissingServletRequestParameterException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "error.missing-parameter", request, ex.getParameterName());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ProblemDetail handleTypeMismatch(
      MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST, "error.type-mismatch", request, ex.getName());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ProblemDetail handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex, HttpServletRequest request) {
    return build(
        HttpStatus.METHOD_NOT_ALLOWED, "error.method-not-allowed", request, ex.getMethod());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ProblemDetail handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
    return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "error.media-type-not-supported", request);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ProblemDetail handleNoResourceFound(
      NoResourceFoundException ex, HttpServletRequest request) {
    return build(HttpStatus.NOT_FOUND, "error.not-found", request);
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ProblemDetail handleDataIntegrityViolation(
      DataIntegrityViolationException ex, HttpServletRequest request) {
    log.warn("Data integrity violation", ex);
    return build(HttpStatus.CONFLICT, "error.conflict", request);
  }

  @ExceptionHandler(DomainException.class)
  public ProblemDetail handleDomainException(DomainException ex, HttpServletRequest request) {
    return build(ex.getStatus(), ex, request);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
    log.error("Unhandled exception", ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal", request);
  }

  private ProblemDetail build(HttpStatus status, DomainException ex, HttpServletRequest request) {
    return build(status, ex.getMessageCode(), request, ex.getArgs());
  }

  private ProblemDetail build(
      HttpStatus status, String messageCode, HttpServletRequest request, Object... args) {
    String detail = resolveMessage(messageCode, args);
    return newProblemDetail(status, messageCode, detail, request);
  }

  private ProblemDetail build(
      HttpStatus status,
      String messageCode,
      HttpServletRequest request,
      Map<String, String> fieldErrors) {
    String detail = resolveMessage(messageCode);
    ProblemDetail problemDetail = newProblemDetail(status, messageCode, detail, request);
    problemDetail.setProperty("errors", fieldErrors);
    return problemDetail;
  }

  private ProblemDetail newProblemDetail(
      HttpStatus status, String messageCode, String detail, HttpServletRequest request) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setType(URI.create(PROBLEM_TYPE_BASE + problemTypeSlug(messageCode)));
    problemDetail.setInstance(URI.create(request.getRequestURI()));
    problemDetail.setProperty("timestamp", OffsetDateTime.now());
    return problemDetail;
  }

  private String problemTypeSlug(String messageCode) {
    int dotIndex = messageCode.indexOf('.');
    return dotIndex >= 0 ? messageCode.substring(dotIndex + 1) : messageCode;
  }

  private String resolveMessage(String messageCode, Object... args) {
    Locale locale = LocaleContextHolder.getLocale();
    return messageSource.getMessage(messageCode, args, messageCode, locale);
  }
}
