package com.brewpay.platform.correlation;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;

/** Safe correlation ids. Callers may send one. Unsafe values are replaced. */
public final class CorrelationIds {

  public static final String HEADER = "X-Correlation-Id";
  public static final String MDC_KEY = "correlationId";

  private static final Pattern SAFE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{7,63}$");

  private CorrelationIds() {}

  public static String resolve(String header) {
    if (header != null && SAFE.matcher(header).matches()) {
      return header;
    }
    return UUID.randomUUID().toString();
  }

  public static Optional<String> current() {
    return Optional.ofNullable(MDC.get(MDC_KEY)).filter(value -> !value.isBlank());
  }
}
