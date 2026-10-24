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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BeneficiaryService {

  static final String ACCOUNT_ONCE = "beneficiaries_account_per_organization";
  private static final int MAX_NAME_LENGTH = 200;
  private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[0-9]{10}$");
  private static final Pattern BANK_CODE = Pattern.compile("^[0-9]{3,6}$");

  private final BeneficiaryRepository beneficiaries;
  private final NameEnquiryProvider provider;
  private final BeneficiaryClaim claims;
  private final AuditLog auditLog;

  public BeneficiaryService(
      BeneficiaryRepository beneficiaries,
      NameEnquiryProvider provider,
      BeneficiaryClaim claims,
      AuditLog auditLog) {
    this.beneficiaries = beneficiaries;
    this.provider = provider;
    this.claims = claims;
    this.auditLog = auditLog;
  }

  public BeneficiaryResponse save(SaveBeneficiaryRequest request) {
    Actor actor = ownerOrDeny();
    String accountNumber = accountNumber(request.destinationAccountNumber());
    String bankCode = bankCode(request.destinationBankCode());
    Optional<Beneficiary> existing = find(actor.organizationId(), accountNumber, bankCode);
    if (existing.isPresent()) {
      return BeneficiaryResponse.from(existing.get());
    }
    String accountName = askProvider(accountNumber, bankCode);
    try {
      return claims.store(actor, accountNumber, bankCode, accountName);
    } catch (DataIntegrityViolationException exception) {
      if (!namesConstraint(exception, ACCOUNT_ONCE)) {
        throw exception;
      }
      return find(actor.organizationId(), accountNumber, bankCode)
          .map(BeneficiaryResponse::from)
          .orElseThrow(() -> exception);
    }
  }

  @Transactional(readOnly = true)
  public BeneficiaryResponse get(UUID id) {
    Actor actor = owner();
    return beneficiaries
        .findByIdAndOrganizationId(id, actor.organizationId())
        .map(BeneficiaryResponse::from)
        .orElseThrow(
            () -> new PlatformException(ErrorCode.NOT_FOUND, "Beneficiary was not found."));
  }

  @Transactional(readOnly = true)
  public List<BeneficiaryResponse> list() {
    Actor actor = owner();
    return beneficiaries.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream()
        .map(BeneficiaryResponse::from)
        .toList();
  }

  private Optional<Beneficiary> find(UUID organizationId, String accountNumber, String bankCode) {
    return beneficiaries.findByOrganizationIdAndDestinationAccountNumberAndDestinationBankCode(
        organizationId, accountNumber, bankCode);
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
          AuditAction.BENEFICIARY_CREATE_DENIED,
          AuditTargetType.BENEFICIARY,
          null);
      Permissions.requireOwner(actor.role());
    }
    return actor;
  }

  private static boolean namesConstraint(Throwable exception, String constraint) {
    Throwable current = exception;
    while (current != null) {
      String message = current.getMessage();
      if (message != null && message.contains(constraint)) {
        return true;
      }
      current = current.getCause();
    }
    return false;
  }
}
