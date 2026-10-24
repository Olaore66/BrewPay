package com.brewpay.payments;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_name_enquiries")
public class AccountNameEnquiry {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "destination_account_number", nullable = false, length = 10)
  private String destinationAccountNumber;

  @Column(name = "destination_bank_code", nullable = false, length = 6)
  private String destinationBankCode;

  @Column(name = "account_name", nullable = false, length = 200)
  private String accountName;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AccountNameEnquiry() {}

  public AccountNameEnquiry(
      UUID id,
      UUID organizationId,
      String destinationAccountNumber,
      String destinationBankCode,
      String accountName,
      UUID createdBy,
      Instant createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.destinationAccountNumber = destinationAccountNumber;
    this.destinationBankCode = destinationBankCode;
    this.accountName = accountName;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getDestinationAccountNumber() {
    return destinationAccountNumber;
  }

  public String getDestinationBankCode() {
    return destinationBankCode;
  }

  public String getAccountName() {
    return accountName;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
