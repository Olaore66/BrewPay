package com.brewpay.payments;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Name-enquiry reads always include the organization id. Do not add a delete method. */
public interface AccountNameEnquiryRepository extends Repository<AccountNameEnquiry, UUID> {

  AccountNameEnquiry save(AccountNameEnquiry enquiry);

  AccountNameEnquiry saveAndFlush(AccountNameEnquiry enquiry);

  Optional<AccountNameEnquiry> findByIdAndOrganizationId(UUID id, UUID organizationId);

  List<AccountNameEnquiry> findByOrganizationIdOrderByCreatedAtAsc(UUID organizationId);
}
