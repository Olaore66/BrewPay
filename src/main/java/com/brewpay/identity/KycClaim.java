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

/** Stores one person verdict and the audit row. The platform does not invent the verdict. */
@Service
public class KycClaim {

  private final KycSubmissionRepository submissions;
  private final UserRepository users;
  private final AuditLog auditLog;
  private final Clock clock;

  public KycClaim(
      KycSubmissionRepository submissions, UserRepository users, AuditLog auditLog, Clock clock) {
    this.submissions = submissions;
    this.users = users;
    this.auditLog = auditLog;
    this.clock = clock;
  }

  @Transactional
  public KycSubmissionResponse store(Actor actor, KycProfile profile, KycStatus status) {
    UserAccount user =
        users
            .findByIdAndOrganizationId(actor.userId(), actor.organizationId())
            .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "User was not found."));
    if (user.getKycStatus() == KycStatus.APPROVED) {
      throw new PlatformException(ErrorCode.CONFLICT, "Identity is already verified.");
    }
    if (user.getKycStatus() == KycStatus.PENDING) {
      throw new PlatformException(
          ErrorCode.CONFLICT, "Identity verification is already in review.");
    }
    KycSubmission submission =
        new KycSubmission(
            UUID.randomUUID(),
            actor.organizationId(),
            actor.userId(),
            profile.dateOfBirth(),
            profile.phone(),
            profile.identificationNumber(),
            status,
            clock.instant());
    submissions.saveAndFlush(submission);
    user.applyVerification(status);
    users.saveAndFlush(user);
    auditLog.record(
        actor.organizationId(),
        actor.userId(),
        AuditAction.KYC_SUBMITTED,
        AuditTargetType.KYC_SUBMISSION,
        submission.getId());
    return KycSubmissionResponse.from(submission);
  }
}
