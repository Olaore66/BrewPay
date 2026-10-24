package com.brewpay.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "journal_lines")
public class JournalLine {

  @Id private UUID id;

  @Column(name = "journal_id", nullable = false)
  private UUID journalId;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "account_id", nullable = false)
  private UUID accountId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 8)
  private Direction direction;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(nullable = false)
  private int position;

  protected JournalLine() {}

  public JournalLine(
      UUID id,
      UUID journalId,
      UUID organizationId,
      UUID accountId,
      Direction direction,
      long amountKobo,
      int position) {
    this.id = id;
    this.journalId = journalId;
    this.organizationId = organizationId;
    this.accountId = accountId;
    this.direction = direction;
    this.amountKobo = amountKobo;
    this.position = position;
  }

  public UUID getId() {
    return id;
  }

  public UUID getJournalId() {
    return journalId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getAccountId() {
    return accountId;
  }

  public Direction getDirection() {
    return direction;
  }

  public long getAmountKobo() {
    return amountKobo;
  }

  public int getPosition() {
    return position;
  }
}
