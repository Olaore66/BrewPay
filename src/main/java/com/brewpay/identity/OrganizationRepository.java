package com.brewpay.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface OrganizationRepository extends Repository<Organization, UUID> {

  Organization save(Organization organization);

  /**
   * Writes the organization before a user row that references it. The user stores a bare
   * organization id, so Hibernate cannot order the two inserts.
   */
  Organization saveAndFlush(Organization organization);

  Optional<Organization> findById(UUID id);
}
