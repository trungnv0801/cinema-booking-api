package com.example.cinema_booking.shared.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
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
import org.springframework.http.ResponseEntity;
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

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final MessageSource messageSource;

  public GlobalExceptionHandler(MessageSource messageSource) {
    this.messageSource = messageSource;
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
      MethodArgumentNotValidException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
      fieldErrors.put(fieldError.getField(), fieldError.getDefaultMessage());
    }
    return build(HttpStatus.BAD_REQUEST, "error.validation-failed", fieldErrors);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
    Map<String, String> fieldErrors = new LinkedHashMap<>();
    for (ConstraintViolation<?> violation : ex.getConstraintViolations()) {
      fieldErrors.put(violation.getPropertyPath().toString(), violation.getMessage());
    }
    return build(HttpStatus.BAD_REQUEST, "error.validation-failed", fieldErrors);
  }

  @ExceptionHandler(HttpMessageNotReadableException.class)
  public ResponseEntity<ErrorResponse> handleMessageNotReadable(
      HttpMessageNotReadableException ex) {
    return build(HttpStatus.BAD_REQUEST, "error.malformed-request");
  }

  @ExceptionHandler(MissingServletRequestParameterException.class)
  public ResponseEntity<ErrorResponse> handleMissingParameter(
      MissingServletRequestParameterException ex) {
    return build(HttpStatus.BAD_REQUEST, "error.missing-parameter", ex.getParameterName());
  }

  @ExceptionHandler(MethodArgumentTypeMismatchException.class)
  public ResponseEntity<ErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
    return build(HttpStatus.BAD_REQUEST, "error.type-mismatch", ex.getName());
  }

  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMethodNotSupported(
      HttpRequestMethodNotSupportedException ex) {
    return build(HttpStatus.METHOD_NOT_ALLOWED, "error.method-not-allowed", ex.getMethod());
  }

  @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
  public ResponseEntity<ErrorResponse> handleMediaTypeNotSupported(
      HttpMediaTypeNotSupportedException ex) {
    return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "error.media-type-not-supported");
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ErrorResponse> handleNoResourceFound(NoResourceFoundException ex) {
    return build(HttpStatus.NOT_FOUND, "error.not-found");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
      DataIntegrityViolationException ex) {
    log.warn("Data integrity violation", ex);
    return build(HttpStatus.CONFLICT, "error.conflict");
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
    log.error("Unhandled exception", ex);
    return build(HttpStatus.INTERNAL_SERVER_ERROR, "error.internal");
  }

  private ResponseEntity<ErrorResponse> build(HttpStatus status, DomainException ex) {
    return build(status, ex.getMessageCode(), ex.getArgs());
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, String messageCode, Object... args) {
    String message = resolveMessage(messageCode, args);
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                OffsetDateTime.now(), status.value(), status.getReasonPhrase(), message));
  }

  private ResponseEntity<ErrorResponse> build(
      HttpStatus status, String messageCode, Map<String, String> fieldErrors) {
    String message = resolveMessage(messageCode);
    return ResponseEntity.status(status)
        .body(
            new ErrorResponse(
                OffsetDateTime.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                fieldErrors));
  }

  private String resolveMessage(String messageCode, Object... args) {
    Locale locale = LocaleContextHolder.getLocale();
    return messageSource.getMessage(messageCode, args, messageCode, locale);
  }
}
