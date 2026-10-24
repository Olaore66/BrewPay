package com.brewpay.ledger;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.access.Permissions;
import com.brewpay.access.Role;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerService {

  static final String CURRENCY = "NGN";
  private static final int DESCRIPTION_LIMIT = 200;
  private static final String ONE_REVERSAL = "journals_one_reversal";

  private final LedgerAccountRepository accounts;
  private final JournalRepository journals;
  private final JournalLineRepository lines;
  private final AuditLog auditLog;
  private final Clock clock;

  public LedgerService(
      LedgerAccountRepository accounts,
      JournalRepository journals,
      JournalLineRepository lines,
      AuditLog auditLog,
      Clock clock) {
    this.accounts = accounts;
    this.journals = journals;
    this.lines = lines;
    this.auditLog = auditLog;
    this.clock = clock;
  }

  @Transactional
  public AccountResponse createAccount(CreateAccountRequest request) {
    Actor actor = owner();
    UUID organizationId = actor.organizationId();
    String code = request.code().trim().toUpperCase(Locale.ROOT);
    String name = request.name().trim();
    if (code.isEmpty() || code.length() > 32) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Account code is invalid.");
    }
    if (name.isEmpty() || name.length() > 160) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Account name is invalid.");
    }
    if (accounts.existsByOrganizationIdAndCode(organizationId, code)) {
      throw new PlatformException(
          ErrorCode.CONFLICT, "An account with this code already exists in the organization.");
    }
    LedgerAccount account =
        new LedgerAccount(
            UUID.randomUUID(), organizationId, code, name, request.type(), clock.instant());
    accounts.saveAndFlush(account);
    auditLog.record(
        organizationId,
        actor.userId(),
        AuditAction.ACCOUNT_CREATED,
        AuditTargetType.ACCOUNT,
        account.getId());
    return AccountResponse.from(account);
  }

  @Transactional(readOnly = true)
  public AccountResponse getAccount(UUID id) {
    Actor actor = owner();
    return accounts
        .findByIdAndOrganizationId(id, actor.organizationId())
        .map(AccountResponse::from)
        .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Account was not found."));
  }

  @Transactional(readOnly = true)
  public List<AccountResponse> listAccounts() {
    Actor actor = owner();
    return accounts.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream()
        .map(AccountResponse::from)
        .toList();
  }

  @Transactional
  public JournalResponse post(PostJournalRequest request) {
    Actor actor =
        ownerOrAuditDenial(AuditAction.JOURNAL_POST_DENIED, AuditTargetType.JOURNAL, null);
    return postBalanced(actor.organizationId(), actor.userId(), request);
  }

  /** Posts a balanced journal for an organization. The caller has already chosen the actor. */
  @Transactional
  public JournalResponse postBalanced(
      UUID organizationId, UUID createdBy, PostJournalRequest request) {
    List<JournalLineRequest> requested = request.lines();
    requireBalanced(requested);
    String description = cleanDescription(request.description());
    for (JournalLineRequest line : requested) {
      requireAccount(organizationId, line.accountId());
    }
    Journal journal =
        new Journal(
            UUID.randomUUID(),
            organizationId,
            description,
            CURRENCY,
            null,
            clock.instant(),
            createdBy);
    journals.saveAndFlush(journal);
    List<JournalLine> stored = storeLines(journal, requested);
    auditLog.record(
        organizationId,
        createdBy,
        AuditAction.JOURNAL_POSTED,
        AuditTargetType.JOURNAL,
        journal.getId());
    return JournalResponse.from(journal, stored);
  }

  @Transactional(readOnly = true)
  public JournalResponse getJournal(UUID id) {
    Actor actor = owner();
    Journal journal =
        journals
            .findByIdAndOrganizationId(id, actor.organizationId())
            .orElseThrow(
                () -> new PlatformException(ErrorCode.NOT_FOUND, "Journal was not found."));
    return JournalResponse.from(journal, linesOf(journal));
  }

  @Transactional(readOnly = true)
  public List<JournalResponse> listJournals() {
    Actor actor = owner();
    return journals.findByOrganizationIdOrderByCreatedAtAsc(actor.organizationId()).stream()
        .map(journal -> JournalResponse.from(journal, linesOf(journal)))
        .toList();
  }

  @Transactional
  public JournalResponse reverse(UUID journalId) {
    Actor actor =
        ownerOrAuditDenial(AuditAction.JOURNAL_REVERSE_DENIED, AuditTargetType.JOURNAL, journalId);
    UUID organizationId = actor.organizationId();
    Journal original =
        journals
            .findByIdAndOrganizationId(journalId, organizationId)
            .orElseThrow(
                () -> new PlatformException(ErrorCode.NOT_FOUND, "Journal was not found."));
    if (journals.existsByOrganizationIdAndReversesJournalId(organizationId, journalId)) {
      throw new PlatformException(ErrorCode.CONFLICT, "Journal is already reversed.");
    }
    List<JournalLine> originalLines = linesOf(original);
    if (originalLines.size() < 2) {
      throw new IllegalStateException("Stored journal has fewer than two lines");
    }
    Journal reversal =
        new Journal(
            UUID.randomUUID(),
            organizationId,
            reversalDescription(original.getDescription()),
            original.getCurrency(),
            original.getId(),
            clock.instant(),
            actor.userId());
    try {
      journals.saveAndFlush(reversal);
      List<JournalLine> stored = new ArrayList<>();
      for (JournalLine line : originalLines) {
        JournalLine swapped =
            new JournalLine(
                UUID.randomUUID(),
                reversal.getId(),
                organizationId,
                line.getAccountId(),
                opposite(line.getDirection()),
                line.getAmountKobo(),
                line.getPosition());
        lines.saveAndFlush(swapped);
        stored.add(swapped);
      }
      auditLog.record(
          organizationId,
          actor.userId(),
          AuditAction.JOURNAL_REVERSED,
          AuditTargetType.JOURNAL,
          reversal.getId());
      return JournalResponse.from(reversal, stored);
    } catch (DataIntegrityViolationException exception) {
      if (namesConstraint(exception, ONE_REVERSAL)) {
        throw new PlatformException(ErrorCode.CONFLICT, "Journal is already reversed.");
      }
      throw exception;
    }
  }

  private List<JournalLine> storeLines(Journal journal, List<JournalLineRequest> requested) {
    List<JournalLine> stored = new ArrayList<>();
    for (int index = 0; index < requested.size(); index++) {
      JournalLineRequest line = requested.get(index);
      JournalLine storedLine =
          new JournalLine(
              UUID.randomUUID(),
              journal.getId(),
              journal.getOrganizationId(),
              line.accountId(),
              line.direction(),
              line.amountKobo(),
              index + 1);
      lines.saveAndFlush(storedLine);
      stored.add(storedLine);
    }
    return stored;
  }

  private List<JournalLine> linesOf(Journal journal) {
    return lines.findByJournalIdAndOrganizationIdOrderByPositionAsc(
        journal.getId(), journal.getOrganizationId());
  }

  private LedgerAccount requireAccount(UUID organizationId, UUID accountId) {
    return accounts
        .findByIdAndOrganizationId(accountId, organizationId)
        .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "Account was not found."));
  }

  private Actor owner() {
    Actor actor = ActorContext.require();
    Permissions.requireOwner(actor.role());
    return actor;
  }

  private Actor ownerOrAuditDenial(AuditAction denial, AuditTargetType targetType, UUID targetId) {
    Actor actor = ActorContext.require();
    if (actor.role() != Role.OWNER) {
      auditLog.recordInNewTransaction(
          actor.organizationId(), actor.userId(), denial, targetType, targetId);
      Permissions.requireOwner(actor.role());
    }
    return actor;
  }

  private static void requireBalanced(List<JournalLineRequest> requested) {
    if (requested == null || requested.size() < 2) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Journal needs at least two lines.");
    }
    long debits = 0;
    long credits = 0;
    for (JournalLineRequest line : requested) {
      Long amountKobo = line.amountKobo();
      if (amountKobo == null || amountKobo <= 0) {
        throw new PlatformException(ErrorCode.BAD_REQUEST, "Journal amount must be positive.");
      }
      try {
        if (line.direction() == Direction.DEBIT) {
          debits = Math.addExact(debits, amountKobo);
        } else if (line.direction() == Direction.CREDIT) {
          credits = Math.addExact(credits, amountKobo);
        } else {
          throw new PlatformException(ErrorCode.BAD_REQUEST, "Journal line direction is invalid.");
        }
      } catch (ArithmeticException exception) {
        throw new PlatformException(ErrorCode.BAD_REQUEST, "Journal amount is too large.");
      }
    }
    if (debits != credits) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Journal is not balanced.");
    }
  }

  private static String cleanDescription(String description) {
    String value = description.trim();
    if (value.isEmpty() || value.length() > DESCRIPTION_LIMIT) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Journal description is invalid.");
    }
    return value;
  }

  private static String reversalDescription(String original) {
    String value = "Reversal of " + original;
    if (value.length() <= DESCRIPTION_LIMIT) {
      return value;
    }
    return value.substring(0, DESCRIPTION_LIMIT);
  }

  private static Direction opposite(Direction direction) {
    return direction == Direction.DEBIT ? Direction.CREDIT : Direction.DEBIT;
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
