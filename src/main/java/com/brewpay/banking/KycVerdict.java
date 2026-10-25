package com.brewpay.banking;

/** The provider's KYB verdict. The platform does not invent an approval. */
public enum KycVerdict {
  PENDING,
  APPROVED,
  REJECTED
}
