package com.brewpay.payments;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.access.Permissions;
import com.brewpay.access.Role;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.banking.CollectionProvider;
import com.brewpay.banking.InboundPayment;
import com.brewpay.banking.ProviderPaymentStatus;
import com.brewpay.ledger.AccountType;
import com.brewpay.ledger.LedgerAccount;
import com.brewpay.ledger.LedgerAccountRepository;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class CollectionService {

  private static final String ONE_EVENT = "collections_event_per_organization";
  private static final String ONE_KEY = "idempotency_records_key_per_action";

  private final CollectionAccountRepository accounts;
  private final CollectionRepository collections;
  private final LedgerAccountRepository ledgerAccounts;
  private final IdempotencyRepository records;
  private final CollectionProvider provider;
  private final CollectionClaim claims;
  private final AuditLog auditLog;
  private final ObjectMapper json;
  private final Clock clock;

  public CollectionService(
      CollectionAccountRepository accounts,
      CollectionRepository collections,
      LedgerAccountRepository ledgerAccounts,
      IdempotencyRepository records,
      CollectionProvider provider,
      CollectionClaim claims,
      AuditLog auditLog,
      ObjectMapper json,
      Clock clock) {
    this.accounts = accounts;
    this.collections = collections;
    this.ledgerAccounts = ledgerAccounts;
    this.records = records;
    this.provider = provider;
    this.claims = claims;
    this.auditLog = auditLog;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public CollectionAccountResponse open(OpenCollectionAccountRequest request) {
    Actor actor = ownerOrDenyOpen();
    UUID organizationId = actor.organizationId();
    if (request.clearingAccountId().equals(request.merchantAccountId())) {
      throw new PlatformException(
          ErrorCode.BAD_REQUEST, "Collection uses two different ledger accounts.");
    }
    LedgerAccount clearing = requireLedgerAccount(organizationId, request.clearingAccountId());
    LedgerAccount merchant = requireLedgerAccount(organizationId, request.merchantAccountId());
    if (clearing.getType() != AccountType.ASSET) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Clearing account must be an asset.");
    }
    if (merchant.getType() != AccountType.LIABILITY) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Merchant account must be a liability.");
    }
    CollectionAccount account =
        new CollectionAccount(
            UUID.randomUUID(),
            organizationId,
            provider.provisionReference(),
            clearing.getId(),
            merchant.getId(),
            actor.userId(),
            clock.instant());
    accounts.saveAndFlush(account);
    auditLog.record(
        organizationId,
        actor.userId(),
        AuditAction.COLLECTION_ACCOUNT_OPENED,
        AuditTargetType.COLLECTION_ACCOUNT,
        account.getId());
    return CollectionAccountResponse.from(account);
  }

  @Transactional(readOnly = true)
  public List<CollectionAccountResponse> listAccounts() {
    Actor actor = owner();
    return accounts.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream()
        .map(CollectionAccountResponse::from)
        .toList();
  }

  @Transactional(readOnly = true)
  public CollectionResponse get(UUID id) {
    Actor actor = owner();
    return collections
        .findByIdAndOrganizationId(id, actor.organizationId())
        .map(CollectionResponse::from)
        .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Collection was not found."));
  }

  @Transactional(readOnly = true)
  public List<CollectionResponse> list() {
    Actor actor = owner();
    return collections.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream()
        .map(CollectionResponse::from)
        .toList();
  }

  public CollectionWebhookResponse accept(InboundPayment payment) {
    String eventId = IdempotencyKeys.require(payment.eventId());
    if (payment.accountReference() == null || payment.accountReference().isBlank()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Webhook could not be read.");
    }
    CollectionAccount account =
        accounts
            .findByExternalReference(payment.accountReference().trim())
            .orElseThrow(
                () ->
                    new PlatformException(
                        ErrorCode.NOT_FOUND, "Collection account was not found."));
    if (payment.status() != ProviderPaymentStatus.SUCCESSFUL) {
      return new CollectionWebhookResponse(payment.status().name(), null);
    }
    if (payment.amountKobo() <= 0) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Collection amount must be positive.");
    }
    String requestHash = hash(eventId, account.getExternalReference(), payment);
    IdempotencyRecord existing =
        records
            .findByOrganizationIdAndActionAndIdempotencyKey(
                account.getOrganizationId(), CollectionClaim.ACTION, eventId)
            .orElse(null);
    if (existing != null) {
      return replay(existing, requestHash);
    }
    try {
      return claims.claim(account, paymentWith(eventId, payment), requestHash);
    } catch (DataIntegrityViolationException exception) {
      if (!namesConstraint(exception, ONE_EVENT) && !namesConstraint(exception, ONE_KEY)) {
        throw exception;
      }
      IdempotencyRecord stored =
          records
              .findByOrganizationIdAndActionAndIdempotencyKey(
                  account.getOrganizationId(), CollectionClaim.ACTION, eventId)
              .orElseThrow(() -> exception);
      return replay(stored, requestHash);
    }
  }

  private CollectionWebhookResponse replay(IdempotencyRecord record, String requestHash) {
    if (!record.getRequestHash().equals(requestHash)) {
      throw new PlatformException(
          ErrorCode.CONFLICT, "Idempotency key was already used with a different request.");
    }
    try {
      return json.readValue(record.getResponseBody(), CollectionWebhookResponse.class);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored collection response could not be read", exception);
    }
  }

  private static InboundPayment paymentWith(String eventId, InboundPayment payment) {
    return new InboundPayment(
        eventId, payment.accountReference(), payment.amountKobo(), payment.status());
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

  private Actor ownerOrDenyOpen() {
    Actor actor = ActorContext.require();
    if (actor.role() != Role.OWNER) {
      auditLog.recordInNewTransaction(
          actor.organizationId(),
          actor.userId(),
          AuditAction.COLLECTION_ACCOUNT_OPEN_DENIED,
          AuditTargetType.COLLECTION_ACCOUNT,
          null);
      Permissions.requireOwner(actor.role());
    }
    return actor;
  }

  private static String hash(String eventId, String reference, InboundPayment payment) {
    String canonical =
        eventId
            + "\n"
            + reference
            + "\n"
            + payment.amountKobo()
            + "\n"
            + payment.status().name()
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
}
