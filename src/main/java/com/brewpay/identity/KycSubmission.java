package com.brewpay.identity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "kyc_submissions")
public class KycSubmission {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "date_of_birth", nullable = false)
  private LocalDate dateOfBirth;

  @Column(nullable = false, length = 16)
  private String phone;

  @Column(name = "identification_number", nullable = false, length = 11)
  private String identificationNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "kyc_status", nullable = false, length = 32)
  private KycStatus kycStatus;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected KycSubmission() {}

  public KycSubmission(
      UUID id,
      UUID organizationId,
      UUID userId,
      LocalDate dateOfBirth,
      String phone,
      String identificationNumber,
      KycStatus kycStatus,
      Instant createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.userId = userId;
    this.dateOfBirth = dateOfBirth;
    this.phone = phone;
    this.identificationNumber = identificationNumber;
    this.kycStatus = kycStatus;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getUserId() {
    return userId;
  }

  public LocalDate getDateOfBirth() {
    return dateOfBirth;
  }

  public String getPhone() {
    return phone;
  }

  public String getIdentificationNumber() {
    return identificationNumber;
  }

  public KycStatus getKycStatus() {
    return kycStatus;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
