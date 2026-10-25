package com.brewpay.identity;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.access.Permissions;
import com.brewpay.access.Role;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.banking.KycApplication;
import com.brewpay.banking.KycProvider;
import com.brewpay.banking.KycVerdict;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KybService {

  private final OrganizationRepository organizations;
  private final KybSubmissionRepository submissions;
  private final KycProvider provider;
  private final KybClaim claims;
  private final AuditLog auditLog;

  public KybService(
      OrganizationRepository organizations,
      KybSubmissionRepository submissions,
      KycProvider provider,
      KybClaim claims,
      AuditLog auditLog) {
    this.organizations = organizations;
    this.submissions = submissions;
    this.provider = provider;
    this.claims = claims;
    this.auditLog = auditLog;
  }

  public KybSubmissionResponse submit(SubmitKybRequest request) {
    Actor actor = ownerOrDeny();
    KybProfile profile = profile(request);
    Organization organization =
        organizations
            .findById(actor.organizationId())
            .orElseThrow(
                () -> new PlatformException(ErrorCode.NOT_FOUND, "Organization was not found."));
    if (organization.getKycStatus() == KycStatus.APPROVED) {
      throw new PlatformException(ErrorCode.CONFLICT, "Business is already verified.");
    }
    if (organization.getKycStatus() == KycStatus.PENDING) {
      throw new PlatformException(
          ErrorCode.CONFLICT, "Business verification is already in review.");
    }
    KycVerdict verdict =
        provider.review(
            new KycApplication(
                organization.getId(),
                organization.getName(),
                profile.tradingName(),
                profile.businessType().name(),
                profile.registrationNumber(),
                profile.addressLine(),
                profile.state(),
                profile.category()));
    return claims.store(actor, profile, toStatus(verdict));
  }

  @Transactional(readOnly = true)
  public KybSubmissionResponse get(UUID id) {
    Actor actor = owner();
    Organization organization = organization(actor.organizationId());
    return submissions
        .findByIdAndOrganizationId(id, actor.organizationId())
        .map(submission -> KybSubmissionResponse.from(submission, organization))
        .orElseThrow(
            () -> new PlatformException(ErrorCode.NOT_FOUND, "KYB submission was not found."));
  }

  @Transactional(readOnly = true)
  public List<KybSubmissionResponse> list() {
    Actor actor = owner();
    Organization organization = organization(actor.organizationId());
    return submissions.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream()
        .map(submission -> KybSubmissionResponse.from(submission, organization))
        .toList();
  }

  private Organization organization(UUID organizationId) {
    return organizations
        .findById(organizationId)
        .orElseThrow(
            () -> new PlatformException(ErrorCode.NOT_FOUND, "Organization was not found."));
  }

  private static KycStatus toStatus(KycVerdict verdict) {
    return switch (verdict) {
      case PENDING -> KycStatus.PENDING;
      case APPROVED -> KycStatus.APPROVED;
      case REJECTED -> KycStatus.REJECTED;
    };
  }

  private static KybProfile profile(SubmitKybRequest request) {
    BusinessType businessType = businessType(request.businessType());
    return new KybProfile(
        required(request.tradingName(), "Trading name is required."),
        businessType,
        registration(businessType, request.registrationNumber()),
        required(request.addressLine(), "Address is required."),
        required(request.state(), "State is required."),
        required(request.category(), "Category is required."));
  }

  private static BusinessType businessType(String value) {
    String trimmed = value == null ? "" : value.trim();
    try {
      return BusinessType.valueOf(trimmed);
    } catch (IllegalArgumentException exception) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Business type is invalid.");
    }
  }

  private static String registration(BusinessType businessType, String value) {
    String registration = value == null ? "" : value.trim();
    if (registration.isEmpty()) {
      if (businessType != BusinessType.SOLE_PROPRIETOR) {
        throw new PlatformException(ErrorCode.BAD_REQUEST, "Registration number is required.");
      }
      return null;
    }
    return registration;
  }

  private static String required(String value, String message) {
    String trimmed = value == null ? "" : value.trim();
    if (trimmed.isEmpty()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, message);
    }
    return trimmed;
  }

  private Actor owner() {
    Actor actor = ActorContext.require();
    Permissions.requireOwner(actor.role());
    return actor;
  }

  private Actor ownerOrDeny() {
    Actor actor = ActorContext.require();
    if (actor.role() != Role.OWNER) {
      auditLog.recordInNewTransaction(
          actor.organizationId(),
          actor.userId(),
          AuditAction.KYB_SUBMIT_DENIED,
          AuditTargetType.KYB_SUBMISSION,
          null);
      Permissions.requireOwner(actor.role());
    }
    return actor;
  }
}
