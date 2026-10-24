package com.brewpay.reconciliation;

import com.brewpay.access.Actor;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.banking.ProviderPaymentStatus;
import com.brewpay.banking.StatementLine;
import com.brewpay.banking.StatementProvider;
import com.brewpay.banking.StatementSubject;
import com.brewpay.payments.Collection;
import com.brewpay.payments.CollectionRepository;
import com.brewpay.payments.IdempotencyRecord;
import com.brewpay.payments.IdempotencyRepository;
import com.brewpay.payments.Transfer;
import com.brewpay.payments.TransferClaim;
import com.brewpay.payments.TransferRepository;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes a run and any new findings. Existing findings stay as they were first written. */
@Service
public class ReconciliationRecorder {

  static final String SUBJECT_ONCE = "reconciliation_findings_subject_once";
  static final String PROVIDER_LINE_ONCE = "reconciliation_findings_provider_line_once";

  private final ReconciliationRunRepository runs;
  private final ReconciliationFindingRepository findings;
  private final CollectionRepository collections;
  private final TransferRepository transfers;
  private final IdempotencyRepository records;
  private final StatementProvider statements;
  private final AuditLog auditLog;
  private final Clock clock;

  public ReconciliationRecorder(
      ReconciliationRunRepository runs,
      ReconciliationFindingRepository findings,
      CollectionRepository collections,
      TransferRepository transfers,
      IdempotencyRepository records,
      StatementProvider statements,
      AuditLog auditLog,
      Clock clock) {
    this.runs = runs;
    this.findings = findings;
    this.collections = collections;
    this.transfers = transfers;
    this.records = records;
    this.statements = statements;
    this.auditLog = auditLog;
    this.clock = clock;
  }

  @Transactional
  public ReconciliationRunResponse store(Actor actor) {
    return write(actor, true);
  }

  /** After a unique-constraint race, keep the first finding and record that this look happened. */
  @Transactional
  public ReconciliationRunResponse reread(Actor actor) {
    return write(actor, false);
  }

  private ReconciliationRunResponse write(Actor actor, boolean insertMissing) {
    List<PlannedFinding> planned = compare(actor.organizationId());
    ReconciliationRun run =
        new ReconciliationRun(
            UUID.randomUUID(), actor.organizationId(), actor.userId(), clock.instant());
    runs.saveAndFlush(run);
    List<ReconciliationFindingResponse> stored = new ArrayList<>();
    for (PlannedFinding plan : planned) {
      Optional<ReconciliationFinding> existing = lookup(actor.organizationId(), plan);
      if (existing.isPresent()) {
        stored.add(ReconciliationFindingResponse.from(existing.get()));
        continue;
      }
      if (!insertMissing) {
        continue;
      }
      ReconciliationFinding finding = plan.create(actor.organizationId(), run.getId(), clock);
      findings.saveAndFlush(finding);
      stored.add(ReconciliationFindingResponse.from(finding));
    }
    auditLog.record(
        actor.organizationId(),
        actor.userId(),
        AuditAction.RECONCILIATION_RUN,
        AuditTargetType.RECONCILIATION_RUN,
        run.getId());
    return new ReconciliationRunResponse(run.getId(), List.copyOf(stored));
  }

  private List<PlannedFinding> compare(UUID organizationId) {
    List<Collection> ourCollections =
        collections.findByOrganizationIdOrderByCreatedAtAsc(organizationId);
    List<Transfer> ourTransfers = transfers.findByOrganizationIdOrderByCreatedAtAsc(organizationId);
    Map<String, Collection> collectionsByEvent = new HashMap<>();
    for (Collection collection : ourCollections) {
      collectionsByEvent.put(collection.getProviderEventId(), collection);
    }
    Map<String, Transfer> transfersByReference = new HashMap<>();
    for (Transfer transfer : ourTransfers) {
      if (transfer.getProviderReference() != null) {
        transfersByReference.put(transfer.getProviderReference(), transfer);
      }
    }
    Set<String> seenEvents = new HashSet<>();
    Set<UUID> seenTransfers = new HashSet<>();
    List<PlannedFinding> planned = new ArrayList<>();
    for (StatementLine line : statements.fetch(organizationId)) {
      PlannedFinding finding =
          againstLine(
              organizationId,
              line,
              collectionsByEvent,
              transfersByReference,
              seenEvents,
              seenTransfers);
      if (finding != null) {
        planned.add(finding);
      }
    }
    for (Collection collection : ourCollections) {
      if (!seenEvents.contains(collection.getProviderEventId())) {
        planned.add(
            PlannedFinding.missingAtProvider(
                StatementSubject.COLLECTION,
                collection.getId(),
                collection.getProviderEventId(),
                ProviderPaymentStatus.SUCCESSFUL,
                collection.getAmountKobo()));
      }
    }
    for (Transfer transfer : ourTransfers) {
      if (transfer.getStatus() == ProviderPaymentStatus.SUCCESSFUL
          && !seenTransfers.contains(transfer.getId())) {
        planned.add(
            PlannedFinding.missingAtProvider(
                StatementSubject.TRANSFER,
                transfer.getId(),
                transfer.getProviderReference(),
                transfer.getStatus(),
                transfer.getAmountKobo()));
      }
    }
    return planned;
  }

  private PlannedFinding againstLine(
      UUID organizationId,
      StatementLine line,
      Map<String, Collection> collectionsByEvent,
      Map<String, Transfer> transfersByReference,
      Set<String> seenEvents,
      Set<UUID> seenTransfers) {
    if (!usable(line)) {
      return null;
    }
    String reference = line.reference().trim();
    if (line.subject() == StatementSubject.COLLECTION) {
      Collection collection = collectionsByEvent.get(reference);
      if (collection == null) {
        return PlannedFinding.missingOnLedger(line, reference);
      }
      seenEvents.add(collection.getProviderEventId());
      return difference(
          StatementSubject.COLLECTION,
          collection.getId(),
          collection.getProviderEventId(),
          ProviderPaymentStatus.SUCCESSFUL,
          collection.getAmountKobo(),
          line);
    }
    Transfer transfer = transfersByReference.get(reference);
    if (transfer == null) {
      transfer = transferSubmittedWith(organizationId, reference);
    }
    if (transfer == null) {
      return PlannedFinding.missingOnLedger(line, reference);
    }
    seenTransfers.add(transfer.getId());
    return difference(
        StatementSubject.TRANSFER,
        transfer.getId(),
        transfer.getProviderReference(),
        transfer.getStatus(),
        transfer.getAmountKobo(),
        line);
  }

  private Transfer transferSubmittedWith(UUID organizationId, String reference) {
    IdempotencyRecord record =
        records
            .findByOrganizationIdAndActionAndIdempotencyKey(
                organizationId, TransferClaim.ACTION, reference)
            .orElse(null);
    if (record == null) {
      return null;
    }
    return transfers.findByIdAndOrganizationId(record.getResourceId(), organizationId).orElse(null);
  }

  private static PlannedFinding difference(
      StatementSubject subject,
      UUID subjectId,
      String ourReference,
      ProviderPaymentStatus ourStatus,
      long ourAmount,
      StatementLine line) {
    if (line.status() != ourStatus) {
      return PlannedFinding.statusMismatch(
          subject, subjectId, ourReference, ourStatus, ourAmount, line);
    }
    if (line.amountKobo() != ourAmount) {
      return PlannedFinding.amountMismatch(
          subject, subjectId, ourReference, ourStatus, ourAmount, line);
    }
    return null;
  }

  private Optional<ReconciliationFinding> lookup(UUID organizationId, PlannedFinding plan) {
    if (plan.subjectId() != null) {
      return findings.findByOrganizationIdAndCodeAndSubjectTypeAndSubjectId(
          organizationId, plan.code(), plan.subjectType(), plan.subjectId());
    }
    return findings.findByOrganizationIdAndCodeAndSubjectTypeAndProviderReference(
        organizationId, plan.code(), plan.subjectType(), plan.providerReference());
  }

  private static boolean usable(StatementLine line) {
    return line != null
        && line.subject() != null
        && line.reference() != null
        && !line.reference().isBlank()
        && line.status() != null
        && line.amountKobo() > 0;
  }

  private record PlannedFinding(
      MismatchCode code,
      StatementSubject subjectType,
      UUID subjectId,
      String providerReference,
      ProviderPaymentStatus providerStatus,
      ProviderPaymentStatus ourStatus,
      Long providerAmountKobo,
      Long ourAmountKobo) {

    static PlannedFinding statusMismatch(
        StatementSubject subject,
        UUID subjectId,
        String ourReference,
        ProviderPaymentStatus ourStatus,
        long ourAmount,
        StatementLine line) {
      return new PlannedFinding(
          MismatchCode.STATUS_MISMATCH,
          subject,
          subjectId,
          ourReference,
          line.status(),
          ourStatus,
          line.amountKobo(),
          ourAmount);
    }

    static PlannedFinding amountMismatch(
        StatementSubject subject,
        UUID subjectId,
        String ourReference,
        ProviderPaymentStatus ourStatus,
        long ourAmount,
        StatementLine line) {
      return new PlannedFinding(
          MismatchCode.AMOUNT_MISMATCH,
          subject,
          subjectId,
          ourReference,
          line.status(),
          ourStatus,
          line.amountKobo(),
          ourAmount);
    }

    static PlannedFinding missingOnLedger(StatementLine line, String reference) {
      return new PlannedFinding(
          MismatchCode.MISSING_ON_LEDGER,
          line.subject(),
          null,
          reference,
          line.status(),
          null,
          line.amountKobo(),
          null);
    }

    static PlannedFinding missingAtProvider(
        StatementSubject subject,
        UUID subjectId,
        String ourReference,
        ProviderPaymentStatus ourStatus,
        long ourAmount) {
      return new PlannedFinding(
          MismatchCode.MISSING_AT_PROVIDER,
          subject,
          subjectId,
          ourReference,
          null,
          ourStatus,
          null,
          ourAmount);
    }

    ReconciliationFinding create(UUID organizationId, UUID runId, Clock clock) {
      return new ReconciliationFinding(
          UUID.randomUUID(),
          organizationId,
          runId,
          code,
          subjectType,
          subjectId,
          providerReference,
          providerStatus,
          ourStatus,
          providerAmountKobo,
          ourAmountKobo,
          clock.instant());
    }
  }
}
