package com.brewpay.identity;

/** Stored KYB state. {@code NOT_SUBMITTED} means the provider has not been asked. */
public enum KycStatus {
  NOT_SUBMITTED,
  PENDING,
  APPROVED,
  REJECTED
}
