package com.brewpay.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Beneficiary reads always include the organization id. Do not add a delete method. */
public interface BeneficiaryRepository extends Repository<Beneficiary, UUID> {

  Beneficiary save(Beneficiary beneficiary);

  Beneficiary saveAndFlush(Beneficiary beneficiary);

  Optional<Beneficiary> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<Beneficiary> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);

  Optional<Beneficiary> findByOrganizationIdAndDestinationAccountNumberAndDestinationBankCode(
      UUID organizationId, String destinationAccountNumber, String destinationBankCode);
}
