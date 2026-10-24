package com.brewpay.banking;

/** The provider did not answer. Callers store UNKNOWN and do not guess an outcome. */
public class ProviderTimeoutException extends RuntimeException {

  public ProviderTimeoutException() {
    super("Provider did not answer");
  }
}
