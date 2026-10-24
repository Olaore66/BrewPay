package com.brewpay.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "journals")
public class Journal {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 200)
  private String description;

  @Column(nullable = false, length = 3)
  private String currency;

  @Column(name = "reverses_journal_id")
  private UUID reversesJournalId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  protected Journal() {}

  public Journal(
      UUID id,
      UUID organizationId,
      String description,
      String currency,
      UUID reversesJournalId,
      Instant createdAt,
      UUID createdBy) {
    this.id = id;
    this.organizationId = organizationId;
    this.description = description;
    this.currency = currency;
    this.reversesJournalId = reversesJournalId;
    this.createdAt = createdAt;
    this.createdBy = createdBy;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getDescription() {
    return description;
  }

  public String getCurrency() {
    return currency;
  }

  public UUID getReversesJournalId() {
    return reversesJournalId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }
}
