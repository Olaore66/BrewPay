package com.brewpay.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Collection reads always include the organization id. Do not add a delete method. */
public interface CollectionRepository extends Repository<Collection, UUID> {

  Collection save(Collection collection);

  Collection saveAndFlush(Collection collection);

  Optional<Collection> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<Collection> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
}
