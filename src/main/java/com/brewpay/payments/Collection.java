package com.brewpay.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collections")
public class Collection {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "collection_account_id", nullable = false)
  private UUID collectionAccountId;

  @Column(name = "provider_event_id", nullable = false, length = 128)
  private String providerEventId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "journal_id", nullable = false)
  private UUID journalId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Collection() {}

  public Collection(
      UUID id,
      UUID organizationId,
      UUID collectionAccountId,
      String providerEventId,
      long amountKobo,
      UUID journalId,
      Instant createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.collectionAccountId = collectionAccountId;
    this.providerEventId = providerEventId;
    this.amountKobo = amountKobo;
    this.journalId = journalId;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getCollectionAccountId() {
    return collectionAccountId;
  }

  public String getProviderEventId() {
    return providerEventId;
  }

  public long getAmountKobo() {
    return amountKobo;
  }

  public UUID getJournalId() {
    return journalId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
