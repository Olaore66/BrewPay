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
@Table(name = "users")
public class UserAccount {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(nullable = false)
  private String email;

  @Column(name = "first_name", nullable = false)
  private String firstName;

  @Column(name = "last_name", nullable = false)
  private String lastName;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false)
  private UserStatus status;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected UserAccount() {}

  public UserAccount(
      UUID id,
      UUID organizationId,
      String email,
      String firstName,
      String lastName,
      UserStatus status,
      Instant createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.email = email;
    this.firstName = firstName;
    this.lastName = lastName;
    this.status = status;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public String getEmail() {
    return email;
  }

  public String getFirstName() {
    return firstName;
  }

  public String getLastName() {
    return lastName;
  }

  public UserStatus getStatus() {
    return status;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
