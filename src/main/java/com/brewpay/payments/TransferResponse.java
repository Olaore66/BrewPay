package com.brewpay.payments;

import java.util.UUID;

public record TransferResponse(
    UUID id,
    UUID organizationId,
    String status,
    long amountKobo,
    String destinationAccountNumber,
    String destinationBankCode,
    String providerReference,
    UUID journalId) {

  static TransferResponse from(Transfer transfer) {
    return new TransferResponse(
        transfer.getId(),
        transfer.getOrganizationId(),
        transfer.getStatus().name(),
        transfer.getAmountKobo(),
        transfer.getDestinationAccountNumber(),
        transfer.getDestinationBankCode(),
        transfer.getProviderReference(),
        transfer.getJournalId());
  }
}
