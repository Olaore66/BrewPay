package com.brewpay.payments;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.access.Permissions;
import com.brewpay.access.Role;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.ledger.JournalLineRequest;
import com.brewpay.ledger.JournalResponse;
import com.brewpay.ledger.PostJournalRequest;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The same key, organization, and action returns the first journal. The key is stored only after
 * the journal commits.
 */
@Service
public class IdempotentJournalPost {

  private static final String ONE_KEY = "idempotency_records_key_per_action";

  private final IdempotencyRepository records;
  private final JournalPostClaim claims;
  private final AuditLog auditLog;
  private final ObjectMapper json;

  public IdempotentJournalPost(
      IdempotencyRepository records,
      JournalPostClaim claims,
      AuditLog auditLog,
      ObjectMapper json) {
    this.records = records;
    this.claims = claims;
    this.auditLog = auditLog;
    this.json = json;
  }

  public JournalResponse post(String key, PostJournalRequest request) {
    Actor actor = owner();
    String cleaned = IdempotencyKeys.require(key);
    String requestHash = hash(request);
    IdempotencyRecord existing =
        records
            .findByOrganizationIdAndActionAndIdempotencyKey(
                actor.organizationId(), JournalPostClaim.ACTION, cleaned)
            .orElse(null);
    if (existing != null) {
      return replay(existing, requestHash);
    }
    try {
      return claims.claim(actor.organizationId(), actor.userId(), cleaned, requestHash, request);
    } catch (DataIntegrityViolationException exception) {
      if (!namesConstraint(exception, ONE_KEY)) {
        throw exception;
      }
      IdempotencyRecord stored =
          records
              .findByOrganizationIdAndActionAndIdempotencyKey(
                  actor.organizationId(), JournalPostClaim.ACTION, cleaned)
              .orElseThrow(() -> exception);
      return replay(stored, requestHash);
    }
  }

  private JournalResponse replay(IdempotencyRecord record, String requestHash) {
    if (!record.getRequestHash().equals(requestHash)) {
      throw new PlatformException(
          ErrorCode.CONFLICT, "Idempotency key was already used with a different request.");
    }
    try {
      return json.readValue(record.getResponseBody(), JournalResponse.class);
    } catch (JacksonException exception) {
      throw new IllegalStateException("Stored journal response could not be read", exception);
    }
  }

  private Actor owner() {
    Actor actor = ActorContext.require();
    if (actor.role() != Role.OWNER) {
      auditLog.recordInNewTransaction(
          actor.organizationId(),
          actor.userId(),
          AuditAction.JOURNAL_POST_DENIED,
          AuditTargetType.JOURNAL,
          null);
      Permissions.requireOwner(actor.role());
    }
    return actor;
  }

  private static String hash(PostJournalRequest request) {
    StringBuilder canonical = new StringBuilder();
    canonical.append(request.description() == null ? "" : request.description().trim());
    canonical.append('\n');
    if (request.lines() != null) {
      for (JournalLineRequest line : request.lines()) {
        canonical
            .append(line.accountId())
            .append('|')
            .append(line.direction())
            .append('|')
            .append(line.amountKobo())
            .append('\n');
      }
    }
    try {
      byte[] digest =
          MessageDigest.getInstance("SHA-256")
              .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
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
