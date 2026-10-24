package com.brewpay.payments;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * A stored result is scoped by organization, action, and key. Do not add {@code findById}, {@code
 * findAll}, or a delete method.
 */
public interface IdempotencyRepository extends Repository<IdempotencyRecord, UUID> {

  IdempotencyRecord save(IdempotencyRecord record);

  IdempotencyRecord saveAndFlush(IdempotencyRecord record);

  Optional<IdempotencyRecord> findByOrganizationIdAndActionAndIdempotencyKey(
      UUID organizationId, String action, String idempotencyKey);
}
