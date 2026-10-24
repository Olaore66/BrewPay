package com.brewpay.ledger;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * Account reads always include the organization id. Do not add {@code findById} or {@code findAll}.
 */
public interface LedgerAccountRepository extends Repository<LedgerAccount, UUID> {

  LedgerAccount save(LedgerAccount account);

  LedgerAccount saveAndFlush(LedgerAccount account);

  Optional<LedgerAccount> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<LedgerAccount> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

  boolean existsByOrganizationIdAndCode(UUID organizationId, String code);
}
