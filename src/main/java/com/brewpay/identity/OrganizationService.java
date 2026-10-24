package com.brewpay.identity;

import com.brewpay.access.Role;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrganizationService {

  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final AuditLog auditLog;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public OrganizationService(
      OrganizationRepository organizations,
      UserRepository users,
      AuditLog auditLog,
      PasswordEncoder passwordEncoder,
      Clock clock) {
    this.organizations = organizations;
    this.users = users;
    this.auditLog = auditLog;
    this.passwordEncoder = passwordEncoder;
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
            passwordEncoder.encode(owner.password()),
            owner.firstName().trim(),
            owner.lastName().trim(),
            Role.OWNER,
            UserStatus.ACTIVE,
            createdAt);
    users.saveAndFlush(account);
    auditLog.record(
        organization.getId(),
        account.getId(),
        AuditAction.USER_CREATED,
        AuditTargetType.USER,
        account.getId());
    return OrganizationResponse.from(organization, account);
  }
}
