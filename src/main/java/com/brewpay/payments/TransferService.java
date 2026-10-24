package com.brewpay.payments;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.access.Permissions;
import com.brewpay.access.Role;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.banking.ProviderPaymentStatus;
import com.brewpay.banking.ProviderTimeoutException;
import com.brewpay.banking.TransferInstruction;
import com.brewpay.banking.TransferProvider;
import com.brewpay.banking.TransferRequest;
import com.brewpay.ledger.AccountType;
import com.brewpay.ledger.LedgerAccount;
import com.brewpay.ledger.LedgerAccountRepository;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class TransferService {

  private static final String ONE_KEY = "idempotency_records_key_per_action";
  private static final Pattern ACCOUNT_NUMBER = Pattern.compile("^[0-9]{10}$");
  private static final Pattern BANK_CODE = Pattern.compile("^[0-9]{3,6}$");

  private final TransferRepository transfers;
  private final LedgerAccountRepository ledgerAccounts;
  private final IdempotencyRepository records;
  private final TransferProvider provider;
  private final TransferClaim claims;
  private final AuditLog auditLog;
  private final ObjectMapper json;

  public TransferService(
      TransferRepository transfers,
      LedgerAccountRepository ledgerAccounts,
      IdempotencyRepository records,
      TransferProvider provider,
      TransferClaim claims,
      AuditLog auditLog,
      ObjectMapper json) {
    this.transfers = transfers;
    this.ledgerAccounts = ledgerAccounts;
    this.records = records;
    this.provider = provider;
    this.claims = claims;
    this.auditLog = auditLog;
    this.json = json;
  }

  public TransferResponse submit(String idempotencyKey, SubmitTransferRequest request) {
    Actor actor = ownerOrDeny();
    String key = IdempotencyKeys.require(idempotencyKey);
    Prepared prepared = prepare(actor.organizationId(), request);
    String requestHash = hash(prepared);
    IdempotencyRecord existing =
        records
            .findByOrganizationIdAndActionAndIdempotencyKey(
                actor.organizationId(), TransferClaim.ACTION, key)
            .orElse(null);
    if (existing != null) {
      return replay(existing, requestHash);
    }
    TransferInstruction instruction = askProvider(key, prepared);
    try {
      return claims.claim(
          actor,
          prepared.clearingAccountId(),
          prepared.merchantAccountId(),
          prepared.amountKobo(),
          prepared.destinationAccountNumber(),
          prepared.destinationBankCode(),
          key,
          requestHash,
          instruction);
    } catch (DataIntegrityViolationException exception) {
      if (!namesConstraint(exception, ONE_KEY)) {
        throw exception;
      }
      IdempotencyRecord stored =
          records
              .findByOrganizationIdAndActionAndIdempotencyKey(
                  actor.organizationId(), TransferClaim.ACTION, key)
              .orElseThrow(() -> exception);
      return replay(stored, requestHash);
    }
  }

  @Transactional(readOnly = true)
  public TransferResponse get(UUID id) {
    Actor actor = owner();
    return transfers
        .findByIdAndOrganizationId(id, actor.organizationId())
        .map(TransferResponse::from)
        .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Transfer was not found."));
  }

  @Transactional(readOnly = true)
  public List<TransferResponse> list() {
    Actor actor = owner();
    return transfers.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream()
        .map(TransferResponse::from)
        .toList();
  }

  private TransferInstruction askProvider(String key, Prepared prepared) {
    try {
      TransferInstruction instruction =
          provider.submit(
              new TransferRequest(
                  key,
                  prepared.amountKobo(),
                  prepared.destinationAccountNumber(),
                  prepared.destinationBankCode()));
      if (instruction == null || instruction.status() == null) {
        throw new IllegalStateException("Provider status is required");
      }
      return instruction;
    } catch (ProviderTimeoutException exception) {
      return new TransferInstruction(null, ProviderPaymentStatus.UNKNOWN);
    }
  }

  private Prepared prepare(UUID organizationId, SubmitTransferRequest request) {
    if (request.amountKobo() <= 0) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Transfer amount must be positive.");
    }
    if (request.clearingAccountId().equals(request.merchantAccountId())) {
      throw new PlatformException(
          ErrorCode.BAD_REQUEST, "Transfer uses two different ledger accounts.");
    }
    LedgerAccount clearing = requireLedgerAccount(organizationId, request.clearingAccountId());
    LedgerAccount merchant = requireLedgerAccount(organizationId, request.merchantAccountId());
    if (clearing.getType() != AccountType.ASSET) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Clearing account must be an asset.");
    }
    if (merchant.getType() != AccountType.LIABILITY) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Merchant account must be a liability.");
    }
    String accountNumber = request.destinationAccountNumber().trim();
    String bankCode = request.destinationBankCode().trim();
    if (!ACCOUNT_NUMBER.matcher(accountNumber).matches()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Destination account number is invalid.");
    }
    if (!BANK_CODE.matcher(bankCode).matches()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Destination bank code is invalid.");
    }
    return new Prepared(
        clearing.getId(), merchant.getId(), request.amountKobo(), accountNumber, bankCode);
  }

  private TransferResponse replay(IdempotencyRecord record, String requestHash) {
    if (!record.getRequestHash().equals(requestHash)) {
      throw new PlatformException(
          ErrorCode.CONFLICT, "Idempotency key was already used with a different request.");
    }
    try {
      return json.readValue(record.getResponseBody(), TransferResponse.class);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored transfer response could not be read", exception);
    }
  }

  private LedgerAccount requireLedgerAccount(UUID organizationId, UUID accountId) {
    return ledgerAccounts
        .findByIdAndOrganizationId(accountId, organizationId)
        .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Account was not found."));
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
          AuditAction.TRANSFER_SUBMIT_DENIED,
          AuditTargetType.TRANSFER,
          null);
      Permissions.requireOwner(actor.role());
    }
    return actor;
  }

  private static String hash(Prepared prepared) {
    String canonical =
        prepared.clearingAccountId()
            + "\n"
            + prepared.merchantAccountId()
            + "\n"
            + prepared.amountKobo()
            + "\n"
            + prepared.destinationAccountNumber()
            + "\n"
            + prepared.destinationBankCode()
            + "\n";
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 is required", exception);
    }
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

  private record Prepared(
      UUID clearingAccountId,
      UUID merchantAccountId,
      long amountKobo,
      String destinationAccountNumber,
      String destinationBankCode) {}
}
