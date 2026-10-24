package com.brewpay.payments;

import com.brewpay.access.Actor;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Stores one successful name enquiry and its audit row. */
@Service
public class NameEnquiryClaim {

  private final AccountNameEnquiryRepository enquiries;
  private final AuditLog auditLog;
  private final Clock clock;

  public NameEnquiryClaim(AccountNameEnquiryRepository enquiries, AuditLog auditLog, Clock clock) {
    this.enquiries = enquiries;
    this.auditLog = auditLog;
    this.clock = clock;
  }

  @Transactional
  public NameEnquiryResponse store(
      Actor actor,
      String destinationAccountNumber,
      String destinationBankCode,
      String accountName) {
    AccountNameEnquiry enquiry =
        new AccountNameEnquiry(
            UUID.randomUUID(),
            actor.organizationId(),
            destinationAccountNumber,
            destinationBankCode,
            accountName,
            actor.userId(),
            clock.instant());
    enquiries.saveAndFlush(enquiry);
    auditLog.record(
        actor.organizationId(),
        actor.userId(),
        AuditAction.ACCOUNT_NAME_ENQUIRED,
        AuditTargetType.ACCOUNT_NAME_ENQUIRY,
        enquiry.getId());
    return NameEnquiryResponse.from(enquiry);
  }
}
