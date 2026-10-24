package com.brewpay.payments;

import com.brewpay.access.Actor;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.banking.ProviderPaymentStatus;
import com.brewpay.banking.TransferInstruction;
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

/** Inserts the transfer, its journal when the provider confirmed, and the idempotency row. */
@Service
public class TransferClaim {

  static final String ACTION = "TRANSFER_SUBMIT";
  private static final int CREATED = 201;

  private final LedgerService ledger;
  private final TransferRepository transfers;
  private final IdempotencyRepository records;
  private final AuditLog auditLog;
  private final ObjectMapper json;
  private final Clock clock;

  public TransferClaim(
      LedgerService ledger,
      TransferRepository transfers,
      IdempotencyRepository records,
      AuditLog auditLog,
      ObjectMapper json,
      Clock clock) {
    this.ledger = ledger;
    this.transfers = transfers;
    this.records = records;
    this.auditLog = auditLog;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public TransferResponse claim(
      Actor actor,
      UUID clearingAccountId,
      UUID merchantAccountId,
      long amountKobo,
      String destinationAccountNumber,
      String destinationBankCode,
      String idempotencyKey,
      String requestHash,
      TransferInstruction instruction) {
    UUID transferId = UUID.randomUUID();
    UUID journalId = null;
    if (instruction.status() == ProviderPaymentStatus.SUCCESSFUL) {
      JournalResponse journal =
          ledger.postBalanced(
              actor.organizationId(),
              actor.userId(),
              new PostJournalRequest(
                  "Transfer " + transferId,
                  List.of(
                      new JournalLineRequest(merchantAccountId, Direction.DEBIT, amountKobo),
                      new JournalLineRequest(clearingAccountId, Direction.CREDIT, amountKobo))));
      journalId = journal.id();
    }
    Transfer transfer =
        new Transfer(
            transferId,
            actor.organizationId(),
            clearingAccountId,
            merchantAccountId,
            amountKobo,
            destinationAccountNumber,
            destinationBankCode,
            instruction.status(),
            instruction.providerReference(),
            journalId,
            actor.userId(),
            clock.instant());
    transfers.saveAndFlush(transfer);
    TransferResponse body = TransferResponse.from(transfer);
    records.saveAndFlush(
        new IdempotencyRecord(
            UUID.randomUUID(),
            actor.organizationId(),
            ACTION,
            idempotencyKey,
            requestHash,
            CREATED,
            write(body),
            transfer.getId(),
            actor.userId(),
            clock.instant()));
    auditLog.record(
        actor.organizationId(),
        actor.userId(),
        AuditAction.TRANSFER_SUBMITTED,
        AuditTargetType.TRANSFER,
        transfer.getId());
    return body;
  }

  private String write(TransferResponse body) {
    try {
      return json.writeValueAsString(body);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Transfer response could not be stored", exception);
    }
  }
}
