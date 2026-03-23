package com.ecom360.shared.infrastructure.web;

import com.ecom360.identity.infrastructure.security.JwtAuthenticationException;
import com.ecom360.shared.domain.exception.*;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(ResourceNotFoundException.class)
  public ResponseEntity<ProblemDetail> handleNotFound(ResourceNotFoundException ex) {
    ProblemDetail d = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    d.setTitle("Resource Not Found");
    d.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(d);
  }

  @ExceptionHandler(ResourceAlreadyExistsException.class)
  public ResponseEntity<ProblemDetail> handleConflict(ResourceAlreadyExistsException ex) {
    ProblemDetail d = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
    d.setTitle("Resource Already Exists");
    d.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.CONFLICT).body(d);
  }

  @ExceptionHandler(BusinessRuleException.class)
  public ResponseEntity<ProblemDetail> handleBusinessRule(BusinessRuleException ex) {
    log.debug("Business rule violation: {}", ex.getMessage());
    ProblemDetail d =
        ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, ex.getMessage());
    d.setTitle("Business Rule Violation");
    d.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(d);
  }

  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex) {
    log.warn("Access denied: {}", ex.getMessage());
    ProblemDetail d = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    d.setTitle("Forbidden");
    d.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(d);
  }

  @ExceptionHandler(DomainException.class)
  public ResponseEntity<ProblemDetail> handleDomain(DomainException ex) {
    ProblemDetail d = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    d.setTitle("Bad Request");
    d.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(d);
  }

  @ExceptionHandler(JwtAuthenticationException.class)
  public ResponseEntity<ProblemDetail> handleJwt(JwtAuthenticationException ex) {
    log.warn("JWT auth failed: {}", ex.getMessage());
    ProblemDetail d = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
    d.setTitle("Unauthorized");
    d.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(d);
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  public ResponseEntity<ProblemDetail> handleSpringAccessDenied(
      org.springframework.security.access.AccessDeniedException ex) {
    log.warn("Access denied: {}", ex.getMessage());
    ProblemDetail d = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
    d.setTitle("Access Denied");
    d.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.FORBIDDEN).body(d);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ProblemDetail> handleValidation(MethodArgumentNotValidException ex) {
    log.debug("Validation failed: {}", ex.getBindingResult().getFieldErrors());
    Map<String, String> errors = new HashMap<>();
    ex.getBindingResult()
        .getAllErrors()
        .forEach(
            error -> {
              String field =
                  error instanceof FieldError
                      ? ((FieldError) error).getField()
                      : error.getObjectName();
              errors.merge(
                  field,
                  error.getDefaultMessage() != null
                      ? error.getDefaultMessage()
                      : "validation error",
                  (a, b) -> a + "; " + b);
            });
    ProblemDetail d = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    d.setTitle("Validation Error");
    d.setProperty("timestamp", Instant.now());
    d.setProperty("errors", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(d);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<ProblemDetail> handleConstraint(ConstraintViolationException ex) {
    Map<String, String> errors =
        ex.getConstraintViolations().stream()
            .collect(
                Collectors.toMap(
                    v -> v.getPropertyPath().toString(), v -> v.getMessage(), (a, b) -> b));
    ProblemDetail d = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
    d.setTitle("Validation Error");
    d.setProperty("timestamp", Instant.now());
    d.setProperty("errors", errors);
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(d);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<ProblemDetail> handleIllegalArgument(IllegalArgumentException ex) {
    ProblemDetail d = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    d.setTitle("Bad Request");
    d.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(d);
  }

  @ExceptionHandler(NoResourceFoundException.class)
  public ResponseEntity<ProblemDetail> handleNoResource(NoResourceFoundException ex) {
    ProblemDetail d = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Resource not found");
    d.setTitle("Not Found");
    d.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(d);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ProblemDetail> handleGeneric(Exception ex) {
    log.error("Unhandled exception: {}", ex.getMessage(), ex);
    ProblemDetail d =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred");
    d.setTitle("Internal Server Error");
    d.setProperty("timestamp", Instant.now());
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(d);
  }
}
