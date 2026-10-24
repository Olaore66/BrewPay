package com.brewpay.payments;

import java.util.UUID;

public record BeneficiaryResponse(
    UUID id,
    UUID organizationId,
    String destinationAccountNumber,
    String destinationBankCode,
    String accountName) {

  static BeneficiaryResponse from(Beneficiary beneficiary) {
    return new BeneficiaryResponse(
        beneficiary.getId(),
        beneficiary.getOrganizationId(),
        beneficiary.getDestinationAccountNumber(),
        beneficiary.getDestinationBankCode(),
        beneficiary.getAccountName());
  }
}
