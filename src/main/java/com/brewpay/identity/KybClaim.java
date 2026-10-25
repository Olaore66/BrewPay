package com.brewpay.identity;

import com.brewpay.access.Actor;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores one provider verdict and the audit row. The platform does not invent the verdict. */
@Service
public class KybClaim {

  private final KybSubmissionRepository submissions;
  private final OrganizationRepository organizations;
  private final AuditLog auditLog;
  private final Clock clock;

  public KybClaim(
      KybSubmissionRepository submissions,
      OrganizationRepository organizations,
      AuditLog auditLog,
      Clock clock) {
    this.submissions = submissions;
    this.organizations = organizations;
    this.auditLog = auditLog;
    this.clock = clock;
  }

  @Transactional
  public KybSubmissionResponse store(Actor actor, KybProfile profile, KycStatus status) {
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
    KybSubmission submission =
        new KybSubmission(
            UUID.randomUUID(),
            actor.organizationId(),
            profile.tradingName(),
            profile.businessType(),
            profile.registrationNumber(),
            profile.addressLine(),
            profile.state(),
            profile.category(),
            status,
            actor.userId(),
            clock.instant());
    submissions.saveAndFlush(submission);
    organization.applyVerification(status);
    organizations.saveAndFlush(organization);
    auditLog.record(
        actor.organizationId(),
        actor.userId(),
        AuditAction.KYB_SUBMITTED,
        AuditTargetType.KYB_SUBMISSION,
        submission.getId());
    return KybSubmissionResponse.from(submission, organization);
  }
}
