package com.brewpay.platform.error;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
  VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
  BAD_REQUEST(HttpStatus.BAD_REQUEST),
  NOT_FOUND(HttpStatus.NOT_FOUND),
  CONFLICT(HttpStatus.CONFLICT),
  INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

  private final HttpStatus status;

  ErrorCode(HttpStatus status) {
    this.status = status;
  }

  public HttpStatus status() {
    return status;
  }
}
