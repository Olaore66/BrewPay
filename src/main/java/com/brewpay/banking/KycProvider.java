package com.brewpay.banking;

/**
 * Who verifies a business and a person. Adapters implement this. The domain does not approve
 * itself.
 */
public interface KycProvider {

  KycVerdict review(KycApplication application);

  KycVerdict reviewPerson(PersonKycApplication application);
}
