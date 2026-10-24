package com.brewpay.audit;

import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Append-only audit. {@link #record} commits with the caller's change. {@link
 * #recordInNewTransaction} stays when the caller rolls back.
 */
@Service
public class AuditLog {

  private final AuditRepository events;
  private final Clock clock;

  public AuditLog(AuditRepository events, Clock clock) {
    this.events = events;
    this.clock = clock;
  }

  /** Joins the caller's transaction, so the audit commits with the change it describes. */
  @Transactional
  public void record(
      UUID organizationId,
      UUID actorUserId,
      AuditAction action,
      AuditTargetType targetType,
      UUID targetId) {
    insert(organizationId, actorUserId, action, targetType, targetId);
  }

  /**
   * Commits even when the caller then rolls back. Use this for a rejected login or a denied action.
   */
  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void recordInNewTransaction(
      UUID organizationId,
      UUID actorUserId,
      AuditAction action,
      AuditTargetType targetType,
      UUID targetId) {
    insert(organizationId, actorUserId, action, targetType, targetId);
  }

  private void insert(
      UUID organizationId,
      UUID actorUserId,
      AuditAction action,
      AuditTargetType targetType,
      UUID targetId) {
    events.save(
        new AuditEvent(
            UUID.randomUUID(),
            organizationId,
            actorUserId,
            action,
            targetType,
            targetId,
            clock.instant()));
  }
}
