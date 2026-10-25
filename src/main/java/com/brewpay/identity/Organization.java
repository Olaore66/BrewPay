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
@Table(name = "organizations")
public class Organization {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private OrganizationStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "kyc_status", nullable = false)
  private KycStatus kycStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected Organization() {}

  public Organization(
      UUID id, String name, OrganizationStatus status, KycStatus kycStatus, Instant createdAt) {
    this.id = id;
    this.name = name;
    this.status = status;
    this.kycStatus = kycStatus;
    this.createdAt = createdAt;
  }

  public void applyVerification(KycStatus kycStatus) {
    this.kycStatus = kycStatus;
    if (kycStatus == KycStatus.APPROVED) {
      this.status = OrganizationStatus.ACTIVE;
    }
  }

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public OrganizationStatus getStatus() {
    return status;
  }

  public KycStatus getKycStatus() {
    return kycStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
