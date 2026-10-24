package com.brewpay.audit;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Append-only. Do not add a delete or update method. */
public interface AuditRepository extends Repository<AuditEvent, UUID> {

  AuditEvent save(AuditEvent event);

  List<AuditEvent> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
}
