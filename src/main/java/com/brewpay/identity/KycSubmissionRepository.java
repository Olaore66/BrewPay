package com.brewpay.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** KYC reads always include the organization id and the user id. Do not add a delete method. */
public interface KycSubmissionRepository extends Repository<KycSubmission, UUID> {

  KycSubmission save(KycSubmission submission);

  KycSubmission saveAndFlush(KycSubmission submission);

  Optional<KycSubmission> findByIdAndOrganizationIdAndUserId(
      UUID id, UUID organizationId, UUID userId);

  List<KycSubmission> findByOrganizationIdAndUserIdOrderByCreatedAtAsc(
      UUID organizationId, UUID userId);
}
