package com.brewpay.ledger;

import java.util.UUID;

public record AccountResponse(UUID id, UUID organizationId, String code, String name, String type) {

  static AccountResponse from(LedgerAccount account) {
    return new AccountResponse(
        account.getId(),
        account.getOrganizationId(),
        account.getCode(),
        account.getName(),
        account.getType().name());
  }
}
