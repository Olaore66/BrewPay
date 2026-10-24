package com.brewpay.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Reads by id include the organization id. {@link #findByExternalReference} is only for a verified
 * webhook, which learns the organization from that reference.
 */
public interface CollectionAccountRepository extends Repository<CollectionAccount, UUID> {

  CollectionAccount save(CollectionAccount account);

  CollectionAccount saveAndFlush(CollectionAccount account);

  Optional<CollectionAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<CollectionAccount> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

  Optional<CollectionAccount> findByExternalReference(String externalReference);
}
