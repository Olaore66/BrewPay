package com.brewpay.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collection_accounts")
public class CollectionAccount {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "external_reference", nullable = false, length = 64)
  private String externalReference;

  @Column(name = "clearing_account_id", nullable = false)
  private UUID clearingAccountId;

  @Column(name = "merchant_account_id", nullable = false)
  private UUID merchantAccountId;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected CollectionAccount() {}

  public CollectionAccount(
      UUID id,
      UUID organizationId,
      String externalReference,
      UUID clearingAccountId,
      UUID merchantAccountId,
      UUID createdBy,
      Instant createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.externalReference = externalReference;
    this.clearingAccountId = clearingAccountId;
    this.merchantAccountId = merchantAccountId;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getExternalReference() {
    return externalReference;
  }

  public UUID getClearingAccountId() {
    return clearingAccountId;
  }

  public UUID getMerchantAccountId() {
    return merchantAccountId;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
