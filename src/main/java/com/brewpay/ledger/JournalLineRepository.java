package com.brewpay.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Line reads always include the organization id. Do not add a delete method. */
public interface JournalLineRepository extends Repository<JournalLine, UUID> {

  JournalLine save(JournalLine line);

  JournalLine saveAndFlush(JournalLine line);

  List<JournalLine> findByJournalIdAndOrganizationIdOrderByPositionAsc(
      UUID journalId, UUID organizationId);
}
