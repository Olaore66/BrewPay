package com.brewpay.banking;

/** Who verifies a business. Adapters implement this. The domain does not approve itself. */
public interface KycProvider {

  KycVerdict review(KycApplication application);
}
