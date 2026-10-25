package com.brewpay.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** KYB reads always include the organization id. Do not add a delete method. */
public interface KybSubmissionRepository extends Repository<KybSubmission, UUID> {

  KybSubmission save(KybSubmission submission);

  KybSubmission saveAndFlush(KybSubmission submission);

  Optional<KybSubmission> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<KybSubmission> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
}
