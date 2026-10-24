package com.brewpay.banking;

import java.util.Optional;

/** Asks a bank for the name on a destination account. Adapters implement this. */
public interface NameEnquiryProvider {

  /**
   * The account name, or empty when the provider has no such account. A timeout throws {@link
   * ProviderTimeoutException}.
   */
  Optional<String> enquire(String destinationAccountNumber, String destinationBankCode);
}
