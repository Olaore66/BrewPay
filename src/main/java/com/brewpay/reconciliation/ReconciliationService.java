package com.brewpay.reconciliation;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.access.Permissions;
import com.brewpay.access.Role;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {

  private final ReconciliationRecorder recorder;
  private final ReconciliationFindingRepository findings;
  private final AuditLog auditLog;

  public ReconciliationService(
      ReconciliationRecorder recorder,
      ReconciliationFindingRepository findings,
      AuditLog auditLog) {
    this.recorder = recorder;
    this.findings = findings;
    this.auditLog = auditLog;
  }

  public ReconciliationRunResponse run() {
    Actor actor = ownerOrDeny();
    try {
      return recorder.store(actor);
    } catch (DataIntegrityViolationException exception) {
      if (!namesConstraint(exception, ReconciliationRecorder.SUBJECT_ONCE)
          && !namesConstraint(exception, ReconciliationRecorder.PROVIDER_LINE_ONCE)) {
        throw exception;
      }
      return recorder.reread(actor);
    }
  }

  @Transactional(readOnly = true)
  public ReconciliationFindingResponse get(UUID id) {
    Actor actor = owner();
    return findings
        .findByIdAndOrganizationId(id, actor.organizationId())
        .map(ReconciliationFindingResponse::from)
        .orElseThrow(
            () ->
                new PlatformException(
                    ErrorCode.NOT_FOUND, "Reconciliation finding was not found."));
  }

  @Transactional(readOnly = true)
  public List<ReconciliationFindingResponse> list() {
    Actor actor = owner();
    return findings.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream()
        .map(ReconciliationFindingResponse::from)
        .toList();
  }

  private Actor owner() {
    Actor actor = ActorContext.require();
    Permissions.requireOwner(actor.role());
    return actor;
  }

  private Actor ownerOrDeny() {
    Actor actor = ActorContext.require();
    if (actor.role() != Role.OWNER) {
      auditLog.recordInNewTransaction(
          actor.organizationId(),
          actor.userId(),
          AuditAction.RECONCILIATION_RUN_DENIED,
          AuditTargetType.RECONCILIATION_RUN,
          null);
      Permissions.requireOwner(actor.role());
    }
    return actor;
  }

  private static boolean namesConstraint(Throwable exception, String constraint) {
    Throwable current = exception;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.contains(constraint)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
