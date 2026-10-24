package com.brewpay.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Transfer reads always include the organization id. Do not add a delete method. */
public interface TransferRepository extends Repository<Transfer, UUID> {

  Transfer save(Transfer transfer);

  Transfer saveAndFlush(Transfer transfer);

  Optional<Transfer> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<Transfer> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
}
