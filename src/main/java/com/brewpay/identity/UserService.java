package com.brewpay.identity;

import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import com.brewpay.platform.tenant.TenantContext;
import java.time.Clock;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final Clock clock;

  public UserService(OrganizationRepository organizations, UserRepository users, Clock clock) {
    this.organizations = organizations;
    this.users = users;
    this.clock = clock;
  }

  @Transactional
  public UserResponse create(CreateUserRequest request) {
    UUID organizationId = TenantContext.require();
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
            request.firstName().trim(),
            request.lastName().trim(),
            UserStatus.ACTIVE,
            clock.instant());
    users.save(account);
    return UserResponse.from(account);
  }

  @Transactional(readOnly = true)
  public UserResponse get(UUID id) {
    UUID organizationId = TenantContext.require();
    return users
        .findByIdAndOrganizationId(id, organizationId)
        .map(UserResponse::from)
        .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "User was not found."));
  }

  @Transactional(readOnly = true)
  public List<UserResponse> list() {
    UUID organizationId = TenantContext.require();
    return users.findByOrganizationIdOrderByCreatedAtAsc(organizationId).stream()
        .map(UserResponse::from)
        .toList();
  }
}
