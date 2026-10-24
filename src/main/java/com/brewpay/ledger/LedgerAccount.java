package com.brewpay.ledger;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false, length = 32)
  private String code;

  @Column(nullable = false, length = 160)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private AccountType type;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected LedgerAccount() {}

  public LedgerAccount(
      UUID id, UUID organizationId, String code, String name, AccountType type, Instant createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.code = code;
    this.name = name;
    this.type = type;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getCode() {
    return code;
  }

  public String getName() {
    return name;
  }

  public AccountType getType() {
    return type;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
