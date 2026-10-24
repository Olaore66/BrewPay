package com.brewpay.payments;

import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.banking.InboundPayment;
import com.brewpay.banking.ProviderPaymentStatus;
import com.brewpay.ledger.Direction;
import com.brewpay.ledger.JournalLineRequest;
import com.brewpay.ledger.JournalResponse;
import com.brewpay.ledger.LedgerService;
import com.brewpay.ledger.PostJournalRequest;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Inserts the collection, its journal, and the idempotency row in one transaction. */
@Service
public class CollectionClaim {

  static final String ACTION = "COLLECTION_RECEIVED";
  private static final int CREATED = 201;

  private final LedgerService ledger;
  private final CollectionRepository collections;
  private final IdempotencyRepository records;
  private final AuditLog auditLog;
  private final ObjectMapper json;
  private final Clock clock;

  public CollectionClaim(
      LedgerService ledger,
      CollectionRepository collections,
      IdempotencyRepository records,
      AuditLog auditLog,
      ObjectMapper json,
      Clock clock) {
    this.ledger = ledger;
    this.collections = collections;
    this.records = records;
    this.auditLog = auditLog;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public CollectionWebhookResponse claim(
      CollectionAccount account, InboundPayment payment, String requestHash) {
    String description = "Collection " + payment.eventId();
    if (description.length() > 200) {
      description = description.substring(0, 200);
    }
    JournalResponse journal =
        ledger.postBalanced(
            account.getOrganizationId(),
            account.getCreatedBy(),
            new PostJournalRequest(
                description,
                List.of(
                    new JournalLineRequest(
                        account.getClearingAccountId(), Direction.DEBIT, payment.amountKobo()),
                    new JournalLineRequest(
                        account.getMerchantAccountId(), Direction.CREDIT, payment.amountKobo()))));
    Collection collection =
        new Collection(
            UUID.randomUUID(),
            account.getOrganizationId(),
            account.getId(),
            payment.eventId(),
            payment.amountKobo(),
            journal.id(),
            clock.instant());
    collections.saveAndFlush(collection);
    CollectionWebhookResponse body =
        new CollectionWebhookResponse(
            ProviderPaymentStatus.SUCCESSFUL.name(), CollectionResponse.from(collection));
    records.saveAndFlush(
        new IdempotencyRecord(
            UUID.randomUUID(),
            account.getOrganizationId(),
            ACTION,
            payment.eventId(),
            requestHash,
            CREATED,
            write(body),
            collection.getId(),
            account.getCreatedBy(),
            clock.instant()));
    auditLog.record(
        account.getOrganizationId(),
        account.getCreatedBy(),
        AuditAction.COLLECTION_CREDITED,
        AuditTargetType.COLLECTION,
        collection.getId());
    return body;
  }

  private String write(CollectionWebhookResponse body) {
    try {
      return json.writeValueAsString(body);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Collection response could not be stored", exception);
    }
  }
}
