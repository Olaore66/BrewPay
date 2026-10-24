package com.brewpay.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_events")
public class AuditEvent {

  @Id private UUID id;

  @Column(name = "organization_id", nullable = false)
  private UUID organizationId;

  @Column(name = "actor_user_id")
  private UUID actorUserId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 64)
  private AuditAction action;

  @Enumerated(EnumType.STRING)
  @Column(name = "target_type", nullable = false, length = 64)
  private AuditTargetType targetType;

  @Column(name = "target_id")
  private UUID targetId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected AuditEvent() {}

  public AuditEvent(
      UUID id,
      UUID organizationId,
      UUID actorUserId,
      AuditAction action,
      AuditTargetType targetType,
      UUID targetId,
      Instant createdAt) {
    this.id = id;
    this.organizationId = organizationId;
    this.actorUserId = actorUserId;
    this.action = action;
    this.targetType = targetType;
    this.targetId = targetId;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public UUID getActorUserId() {
    return actorUserId;
  }

  public AuditAction getAction() {
    return action;
  }

  public AuditTargetType getTargetType() {
    return targetType;
  }

  public UUID getTargetId() {
    return targetId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
