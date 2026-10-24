package com.brewpay.payments;

import java.util.UUID;

public record CollectionAccountResponse(
    UUID id,
    UUID organizationId,
    String externalReference,
    UUID clearingAccountId,
    UUID merchantAccountId) {

  static CollectionAccountResponse from(CollectionAccount account) {
    return new CollectionAccountResponse(
        account.getId(),
        account.getOrganizationId(),
        account.getExternalReference(),
        account.getClearingAccountId(),
        account.getMerchantAccountId());
  }
}
