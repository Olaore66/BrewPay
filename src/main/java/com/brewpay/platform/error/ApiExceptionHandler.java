package com.brewpay.platform.error;

import com.brewpay.platform.correlation.CorrelationIds;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(PlatformException.class)
  ResponseEntity<ApiError> platform(PlatformException exception) {
    return respond(exception.code(), exception.getMessage(), List.of());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException exception) {
    List<ApiError.FieldViolation> details =
        exception.getBindingResult().getFieldErrors().stream()
            .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
            .toList();
    return respond(ErrorCode.VALIDATION_FAILED, "Request validation failed.", details);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MissingServletRequestParameterException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<ApiError> badRequest(Exception exception) {
    log.debug("Rejected request", exception);
    return respond(ErrorCode.BAD_REQUEST, "Request could not be read.", List.of());
  }

  @ExceptionHandler(NoResourceFoundException.class)
  ResponseEntity<ApiError> missing(NoResourceFoundException exception) {
    return respond(ErrorCode.NOT_FOUND, "Resource was not found.", List.of());
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception exception) {
    log.error("Unhandled error", exception);
    return respond(ErrorCode.INTERNAL_ERROR, "Something went wrong.", List.of());
  }

  private ResponseEntity<ApiError> respond(
      ErrorCode code, String message, List<ApiError.FieldViolation> details) {
    String correlationId = CorrelationIds.current().orElse("missing");
    ApiError body = new ApiError(code.name(), message, correlationId, details);
    return ResponseEntity.status(code.status()).body(body);
  }
}
