package com.brewpay.payments;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.access.Permissions;
import com.brewpay.access.Role;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.banking.NameEnquiryProvider;
import com.brewpay.banking.ProviderTimeoutException;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NameEnquiryService {

  private static final int MAX_NAME_LENGTH = 200;
  private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[0-9]{10}$");
  private static final Pattern BANK_CODE = Pattern.compile("^[0-9]{3,6}$");

  private final AccountNameEnquiryRepository enquiries;
  private final NameEnquiryProvider provider;
  private final NameEnquiryClaim claims;
  private final AuditLog auditLog;

  public NameEnquiryService(
      AccountNameEnquiryRepository enquiries,
      NameEnquiryProvider provider,
      NameEnquiryClaim claims,
      AuditLog auditLog) {
    this.enquiries = enquiries;
    this.provider = provider;
    this.claims = claims;
    this.auditLog = auditLog;
  }

  public NameEnquiryResponse enquire(NameEnquiryRequest request) {
    Actor actor = ownerOrDeny();
    String accountNumber = accountNumber(request.destinationAccountNumber());
    String bankCode = bankCode(request.destinationBankCode());
    String accountName = askProvider(accountNumber, bankCode);
    return claims.store(actor, accountNumber, bankCode, accountName);
  }

  @Transactional(readOnly = true)
  public NameEnquiryResponse get(UUID id) {
    Actor actor = owner();
    return enquiries
        .findByIdAndOrganizationId(id, actor.organizationId())
        .map(NameEnquiryResponse::from)
        .orElseThrow(
            () ->
                new PlatformException(ErrorCode.NOT_FOUND, "Account name enquiry was not found."));
  }

  @Transactional(readOnly = true)
  public List<NameEnquiryResponse> list() {
    Actor actor = owner();
    return enquiries.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream()
        .map(NameEnquiryResponse::from)
        .toList();
  }

  private String askProvider(String accountNumber, String bankCode) {
    Optional<String> name;
    try {
      name = provider.enquire(accountNumber, bankCode);
    } catch (ProviderTimeoutException exception) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Provider did not answer.");
    }
    String trimmed = name == null ? "" : name.orElse("").trim();
    if (trimmed.isEmpty()) {
      throw new PlatformException(ErrorCode.NOT_FOUND, "Account name was not found.");
    }
    if (trimmed.length() > MAX_NAME_LENGTH) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Account name is invalid.");
    }
    return trimmed;
  }

  private static String accountNumber(String value) {
    String accountNumber = value.trim();
    if (!ACCOUNT_NUMBER.matcher(accountNumber).matches()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Destination account number is invalid.");
    }
    return accountNumber;
  }

  private static String bankCode(String value) {
    String bankCode = value.trim();
    if (!BANK_CODE.matcher(bankCode).matches()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Destination bank code is invalid.");
    }
    return bankCode;
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
          AuditAction.ACCOUNT_NAME_ENQUIRY_DENIED,
          AuditTargetType.ACCOUNT_NAME_ENQUIRY,
          null);
      Permissions.requireOwner(actor.role());
    }
    return actor;
  }
}
