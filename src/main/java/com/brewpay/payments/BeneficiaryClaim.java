package com.brewpay.payments;

import com.brewpay.access.Actor;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores one beneficiary and its audit row. The destination is not changed later. */
@Service
public class BeneficiaryClaim {

  private final BeneficiaryRepository beneficiaries;
  private final AuditLog auditLog;
  private final Clock clock;

  public BeneficiaryClaim(BeneficiaryRepository beneficiaries, AuditLog auditLog, Clock clock) {
    this.beneficiaries = beneficiaries;
    this.auditLog = auditLog;
    this.clock = clock;
  }

  @Transactional
  public BeneficiaryResponse store(
      Actor actor,
      String destinationAccountNumber,
      String destinationBankCode,
      String accountName) {
    Beneficiary beneficiary =
        new Beneficiary(
            UUID.randomUUID(),
            actor.organizationId(),
            destinationAccountNumber,
            destinationBankCode,
            accountName,
            actor.userId(),
            clock.instant());
    beneficiaries.saveAndFlush(beneficiary);
    auditLog.record(
        actor.organizationId(),
        actor.userId(),
        AuditAction.BENEFICIARY_CREATED,
        AuditTargetType.BENEFICIARY,
        beneficiary.getId());
    return BeneficiaryResponse.from(beneficiary);
  }
}
