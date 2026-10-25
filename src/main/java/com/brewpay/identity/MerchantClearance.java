package com.brewpay.identity;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.access.Role;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Money stays closed until the provider approves the business and the owner. Staff fall through so
 * the route still returns the permission denial. A request with no actor, including the public
 * webhook, is left alone.
 */
public class MerchantClearance implements HandlerInterceptor {

  static final String NOT_COMPLETE = "Business verification is not complete.";
  static final String IDENTITY_NOT_COMPLETE = "Identity verification is not complete.";

  private static final List<String> MONEY =
      List.of(
          "/api/v1/ledger/",
          "/api/v1/collection-accounts",
          "/api/v1/collections",
          "/api/v1/transfers",
          "/api/v1/account-name-enquiries",
          "/api/v1/beneficiaries",
          "/api/v1/reconciliation-runs",
          "/api/v1/reconciliation-findings");

  private final OrganizationRepository organizations;
  private final UserRepository users;

  public MerchantClearance(OrganizationRepository organizations, UserRepository users) {
    this.organizations = organizations;
    this.users = users;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (!isMoney(request.getRequestURI())) {
      return true;
    }
    Optional<Actor> current = ActorContext.current();
    if (current.isEmpty() || current.get().role() != Role.OWNER) {
      return true;
    }
    Actor actor = current.get();
    KycStatus status =
        organizations
            .findById(actor.organizationId())
            .map(Organization::getKycStatus)
            .orElse(KycStatus.NOT_SUBMITTED);
    if (status != KycStatus.APPROVED) {
      throw new PlatformException(ErrorCode.FORBIDDEN, NOT_COMPLETE);
    }
    KycStatus person =
        users
            .findByIdAndOrganizationId(actor.userId(), actor.organizationId())
            .map(UserAccount::getKycStatus)
            .orElse(KycStatus.NOT_SUBMITTED);
    if (person != KycStatus.APPROVED) {
      throw new PlatformException(ErrorCode.FORBIDDEN, IDENTITY_NOT_COMPLETE);
    }
    return true;
  }

  private static boolean isMoney(String uri) {
    for (String prefix : MONEY) {
      if (uri.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
