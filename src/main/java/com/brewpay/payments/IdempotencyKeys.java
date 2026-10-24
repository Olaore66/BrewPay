package com.brewpay.payments;

import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.util.regex.Pattern;

/** Client key for a request that must not be applied twice. */
public final class IdempotencyKeys {

  public static final String HEADER = "Idempotency-Key";

  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._:-]{7,127}$");

  private IdempotencyKeys() {}

  public static String require(String key) {
    if (key == null || key.isBlank()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Idempotency key is required.");
    }
    String value = key.trim();
    if (!SAFE.matcher(value).matches()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Idempotency key is invalid.");
    }
    return value;
  }
}
