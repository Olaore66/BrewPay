package com.brewpay.identity;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Child reads always include the organization id. Do not add {@code findById} or {@code findAll}.
 */
public interface UserRepository extends Repository<UserAccount, UUID> {

  UserAccount save(UserAccount user);

  UserAccount saveAndFlush(UserAccount user);

  Optional<UserAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<UserAccount> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

  boolean existsByOrganizationIdAndEmail(UUID organizationId, String email);

  Optional<UserAccount> findByOrganizationIdAndEmail(UUID organizationId, String email);
}
