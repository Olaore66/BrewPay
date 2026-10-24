package com.brewpay.identity;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * A session is loaded by the hash of its secret, or by id inside its organization. Do not add
 * {@code findAll}.
 */
public interface SessionRepository extends Repository<UserSession, UUID> {

  UserSession save(UserSession session);

  UserSession saveAndFlush(UserSession session);

  Optional<UserSession> findByTokenHash(String tokenHash);

  Optional<UserSession> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
