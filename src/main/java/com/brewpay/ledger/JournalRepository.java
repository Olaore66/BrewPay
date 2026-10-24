package com.brewpay.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Journal reads always include the organization id. Do not add {@code findById} or {@code findAll}.
 */
public interface JournalRepository extends Repository<Journal, UUID> {

  Journal save(Journal journal);

  Journal saveAndFlush(Journal journal);

  Optional<Journal> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<Journal> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

  boolean existsByOrganizationIdAndReversesJournalId(UUID organizationId, UUID reversesJournalId);
}
