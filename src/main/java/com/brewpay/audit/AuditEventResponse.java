package com.brewpay.audit;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
    UUID id,
    UUID organizationId,
    UUID actorUserId,
    String action,
    String targetType,
    UUID targetId,
    Instant createdAt) {

  static AuditEventResponse from(AuditEvent event) {
    return new AuditEventResponse(
        event.getId(),
        event.getOrganizationId(),
        event.getActorUserId(),
        event.getAction().name(),
        event.getTargetType().name(),
        event.getTargetId(),
        event.getCreatedAt());
  }
}
