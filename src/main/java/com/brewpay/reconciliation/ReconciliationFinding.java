package com.brewpay.reconciliation;

import com.brewpay.banking.ProviderPaymentStatus;
import com.brewpay.banking.StatementSubject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_findings")
public class ReconciliationFinding {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "run_id", nullable = false)
  private UUID runId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 32)
  private MismatchCode code;

  @Enumerated(EnumType.STRING)
  @Column(name = "subject_type", nullable = false, length = 16)
  private StatementSubject subjectType;

  @Column(name = "subject_id")
  private UUID subjectId;

  @Column(name = "provider_reference", length = 128)
  private String providerReference;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider_status", length = 16)
  private ProviderPaymentStatus providerStatus;

  @Enumerated(EnumType.STRING)
  @Column(name = "our_status", length = 16)
  private ProviderPaymentStatus ourStatus;

  @Column(name = "provider_amount_kobo")
  private Long providerAmountKobo;

  @Column(name = "our_amount_kobo")
  private Long ourAmountKobo;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected ReconciliationFinding() {}

  public ReconciliationFinding(
      UUID id,
      UUID organizationId,
      UUID runId,
      MismatchCode code,
      StatementSubject subjectType,
      UUID subjectId,
      String providerReference,
      ProviderPaymentStatus providerStatus,
      ProviderPaymentStatus ourStatus,
      Long providerAmountKobo,
      Long ourAmountKobo,
      Instant createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.runId = runId;
    this.code = code;
    this.subjectType = subjectType;
    this.subjectId = subjectId;
    this.providerReference = providerReference;
    this.providerStatus = providerStatus;
    this.ourStatus = ourStatus;
    this.providerAmountKobo = providerAmountKobo;
    this.ourAmountKobo = ourAmountKobo;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getRunId() {
    return runId;
  }

  public MismatchCode getCode() {
    return code;
  }

  public StatementSubject getSubjectType() {
    return subjectType;
  }

  public UUID getSubjectId() {
    return subjectId;
  }

  public String getProviderReference() {
    return providerReference;
  }

  public ProviderPaymentStatus getProviderStatus() {
    return providerStatus;
  }

  public ProviderPaymentStatus getOurStatus() {
    return ourStatus;
  }

  public Long getProviderAmountKobo() {
    return providerAmountKobo;
  }

  public Long getOurAmountKobo() {
    return ourAmountKobo;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
