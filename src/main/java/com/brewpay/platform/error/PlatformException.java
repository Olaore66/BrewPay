package com.brewpay.platform.error;

/** A failure we chose to show the caller. The message is safe to return. */
public class PlatformException extends RuntimeException {

  private final ErrorCode code;

  public PlatformException(ErrorCode code, String message) {
    super(message);
    this.code = code;
  }

  public ErrorCode code() {
    return code;
  }
}
