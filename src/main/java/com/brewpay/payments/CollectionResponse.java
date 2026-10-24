package com.brewpay.payments;

import java.util.UUID;

public record CollectionResponse(
    UUID id,
    UUID organizationId,
    UUID collectionAccountId,
    String providerEventId,
    long amountKobo,
    UUID journalId) {

  static CollectionResponse from(Collection collection) {
    return new CollectionResponse(
        collection.getId(),
        collection.getOrganizationId(),
        collection.getCollectionAccountId(),
        collection.getProviderEventId(),
        collection.getAmountKobo(),
        collection.getJournalId());
  }
}
