package com.brewpay.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "kyb_submissions")
public class KybSubmission {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "trading_name", nullable = false, length = 160)
  private String tradingName;

  @Enumerated(EnumType.STRING)
  @Column(name = "business_type", nullable = false, length = 32)
  private BusinessType businessType;

  @Column(name = "registration_number", length = 40)
  private String registrationNumber;

  @Column(name = "address_line", nullable = false, length = 200)
  private String addressLine;

  @Column(nullable = false, length = 40)
  private String state;

  @Column(nullable = false, length = 80)
  private String category;

  @Enumerated(EnumType.STRING)
  @Column(name = "kyc_status", nullable = false, length = 32)
  private KycStatus kycStatus;

  @Column(name = "created_by", nullable = false)
  private UUID createdBy;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected KybSubmission() {}

  public KybSubmission(
      UUID id,
      UUID organizationId,
      String tradingName,
      BusinessType businessType,
      String registrationNumber,
      String addressLine,
      String state,
      String category,
      KycStatus kycStatus,
      UUID createdBy,
      Instant createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.tradingName = tradingName;
    this.businessType = businessType;
    this.registrationNumber = registrationNumber;
    this.addressLine = addressLine;
    this.state = state;
    this.category = category;
    this.kycStatus = kycStatus;
    this.createdBy = createdBy;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getTradingName() {
    return tradingName;
  }

  public BusinessType getBusinessType() {
    return businessType;
  }

  public String getRegistrationNumber() {
    return registrationNumber;
  }

  public String getAddressLine() {
    return addressLine;
  }

  public String getState() {
    return state;
  }

  public String getCategory() {
    return category;
  }

  public KycStatus getKycStatus() {
    return kycStatus;
  }

  public UUID getCreatedBy() {
    return createdBy;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
