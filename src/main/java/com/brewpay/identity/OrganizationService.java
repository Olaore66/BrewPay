package com.brewpay.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final Clock clock;

  public OrganizationService(
      OrganizationRepository organizations, UserRepository users, Clock clock) {
    this.organizations = organizations;
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public OrganizationResponse create(CreateOrganizationRequest request) {
    Instant createdAt = clock.instant();
    Organization organization =
        new Organization(
            UUID.randomUUID(), request.name().trim(), OrganizationStatus.ACTIVE, createdAt);
    organizations.saveAndFlush(organization);

    CreateOrganizationRequest.Owner owner = request.owner();
    UserAccount account =
        new UserAccount(
            UUID.randomUUID(),
            organization.getId(),
            owner.email().trim().toLowerCase(Locale.ROOT),
            owner.firstName().trim(),
            owner.lastName().trim(),
            UserStatus.ACTIVE,
            createdAt);
    users.save(account);
    return OrganizationResponse.from(organization, account);
  }
}
