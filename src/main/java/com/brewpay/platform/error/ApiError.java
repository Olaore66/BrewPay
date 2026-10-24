package com.brewpay.platform.error;

import java.util.List;

/**
 * The only error body this API returns.
 *
 * <p>{@code details} is empty when the failure is not a field validation error.
 */
public record ApiError(
    String code, String message, String correlationId, List<FieldViolation> details) {

  public ApiError {
    details = details == null ? List.of() : List.copyOf(details);
  }

  public record FieldViolation(String field, String message) {}
}
