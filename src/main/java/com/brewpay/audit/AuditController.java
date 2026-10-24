package com.brewpay.audit;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.access.Permissions;
import com.brewpay.access.Role;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit-events")
public class AuditController {

  private final AuditRepository events;
  private final AuditLog auditLog;

  public AuditController(AuditRepository events, AuditLog auditLog) {
    this.events = events;
    this.auditLog = auditLog;
  }

  @GetMapping
  public List<AuditEventResponse> list() {
    Actor actor = ActorContext.require();
    if (actor.role() != Role.OWNER) {
      auditLog.recordInNewTransaction(
          actor.organizationId(),
          actor.userId(),
          AuditAction.AUDIT_READ_DENIED,
          AuditTargetType.AUDIT,
          null);
      Permissions.requireOwner(actor.role());
    }
    return events.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream()
        .map(AuditEventResponse::from)
        .toList();
  }
}
