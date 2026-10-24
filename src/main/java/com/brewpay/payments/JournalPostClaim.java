package com.brewpay.payments;

import com.brewpay.ledger.JournalResponse;
import com.brewpay.ledger.LedgerService;
import com.brewpay.ledger.PostJournalRequest;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Inserts the journal and the idempotency row in one transaction. */
@Service
public class JournalPostClaim {

  static final String ACTION = "JOURNAL_POST";
  private static final int CREATED = 201;

  private final LedgerService ledger;
  private final IdempotencyRepository records;
  private final ObjectMapper json;
  private final Clock clock;

  public JournalPostClaim(
      LedgerService ledger, IdempotencyRepository records, ObjectMapper json, Clock clock) {
    this.ledger = ledger;
    this.records = records;
    this.json = json;
    this.clock = clock;
  }

  @Transactional
  public JournalResponse claim(
      UUID organizationId,
      UUID actorUserId,
      String key,
      String requestHash,
      PostJournalRequest request) {
    JournalResponse response = ledger.post(request);
    records.saveAndFlush(
        new IdempotencyRecord(
            UUID.randomUUID(),
            organizationId,
            ACTION,
            key,
            requestHash,
            CREATED,
            write(response),
            response.id(),
            actorUserId,
            clock.instant()));
    return response;
  }

  private String write(JournalResponse response) {
    try {
      return json.writeValueAsString(response);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Journal response could not be stored", exception);
    }
  }
}
