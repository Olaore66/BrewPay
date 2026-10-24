package com.brewpay.reconciliation;

import java.util.UUID;
import org.springframework.data.repository.Repository;

/** A run is scoped by the organization. Do not add a delete method. */
public interface ReconciliationRunRepository extends Repository<ReconciliationRun, UUID> {

  ReconciliationRun save(ReconciliationRun run);

  ReconciliationRun saveAndFlush(ReconciliationRun run);
}
