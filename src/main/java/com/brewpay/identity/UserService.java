package com.brewpay.identity;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.access.Permissions;
import com.brewpay.access.Role;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final AuditLog auditLog;
  private final PasswordEncoder passwordEncoder;
  private final Clock clock;

  public UserService(
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
  public UserResponse create(CreateUserRequest request) {
    Actor actor = ActorContext.require();
    if (actor.role() != Role.OWNER) {
      auditLog.recordInNewTransaction(
          actor.organizationId(),
          actor.userId(),
          AuditAction.USER_CREATE_DENIED,
          AuditTargetType.USER,
          null);
      Permissions.requireOwner(actor.role());
    }
    UUID organizationId = actor.organizationId();
    if (organizations.findById(organizationId).isEmpty()) {
      throw new PlatformException(ErrorCode.NOT_FOUND, "Organization was not found.");
    }
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    if (users.existsByOrganizationIdAndEmail(organizationId, email)) {
      throw new PlatformException(
          ErrorCode.CONFLICT, "A user with this email already exists in the organization.");
    }
    UserAccount account =
        new UserAccount(
            UUID.randomUUID(),
            organizationId,
            email,
            passwordEncoder.encode(request.password()),
            request.firstName().trim(),
            request.lastName().trim(),
            Role.STAFF,
            UserStatus.ACTIVE,
            clock.instant());
    users.saveAndFlush(account);
    auditLog.record(
        organizationId,
        actor.userId(),
        AuditAction.USER_CREATED,
        AuditTargetType.USER,
        account.getId());
    return UserResponse.from(account);
  }

  @Transactional(readOnly = true)
  public UserResponse get(UUID id) {
    UUID organizationId = ActorContext.require().organizationId();
    return users
        .findByIdAndOrganizationId(id, organizationId)
        .map(UserResponse::from)
        .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "User was not found."));
  }

  @Transactional(readOnly = true)
  public List<UserResponse> list() {
    UUID organizationId = ActorContext.require().organizationId();
    return users.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
        .map(UserResponse::from)
        .toList();
  }
}
