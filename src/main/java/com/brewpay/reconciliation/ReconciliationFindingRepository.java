package com.brewpay.reconciliation;

import com.brewpay.banking.StatementSubject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Finding reads always include the organization id. Do not add a delete method. */
public interface ReconciliationFindingRepository extends Repository<ReconciliationFinding, UUID> {

  ReconciliationFinding save(ReconciliationFinding finding);

  ReconciliationFinding saveAndFlush(ReconciliationFinding finding);

  Optional<ReconciliationFinding> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<ReconciliationFinding> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

  Optional<ReconciliationFinding> findByOrganizationIdAndCodeAndSubjectTypeAndSubjectId(
      UUID organizationId, MismatchCode code, StatementSubject subjectType, UUID subjectId);

  Optional<ReconciliationFinding> findByOrganizationIdAndCodeAndSubjectTypeAndProviderReference(
      UUID organizationId,
      MismatchCode code,
      StatementSubject subjectType,
      String providerReference);
}
