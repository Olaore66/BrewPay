package com.brewpay.payments;

import com.brewpay.banking.ProviderPaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "transfers")
public class Transfer {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "clearing_account_id", nullable = false)
  private UUID clearingAccountId;

  @Column(name = "merchant_account_id", nullable = false)
  private UUID merchantAccountId;

  @Column(name = "amount_kobo", nullable = false)
  private long amountKobo;

  @Column(name = "destination_account_number", nullable = false, length = 10)
  private String destinationAccountNumber;

  @Column(name = "destination_bank_code", nullable = false, length = 6)
  private String destinationBankCode;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 16)
  private ProviderPaymentStatus status;

  @Column(name = "provider_reference", length = 64)
  private String providerReference;

  @Column(name = "journal_id")
  private UUID journalId;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Transfer() {}

  public Transfer(
      UUID id,
      UUID organizationId,
      UUID clearingAccountId,
      UUID merchantAccountId,
      long amountKobo,
      String destinationAccountNumber,
      String destinationBankCode,
      ProviderPaymentStatus status,
      String providerReference,
      UUID journalId,
      UUID createdBy,
      Instant createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.clearingAccountId = clearingAccountId;
    this.merchantAccountId = merchantAccountId;
    this.amountKobo = amountKobo;
    this.destinationAccountNumber = destinationAccountNumber;
    this.destinationBankCode = destinationBankCode;
    this.status = status;
    this.providerReference = providerReference;
    this.journalId = journalId;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getClearingAccountId() {
    return clearingAccountId;
  }

  public UUID getMerchantAccountId() {
    return merchantAccountId;
  }

  public long getAmountKobo() {
    return amountKobo;
  }

  public String getDestinationAccountNumber() {
    return destinationAccountNumber;
  }

  public String getDestinationBankCode() {
    return destinationBankCode;
  }

  public ProviderPaymentStatus getStatus() {
    return status;
  }

  public String getProviderReference() {
    return providerReference;
  }

  public UUID getJournalId() {
    return journalId;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
