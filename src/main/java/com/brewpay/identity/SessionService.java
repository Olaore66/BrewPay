package com.brewpay.identity;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.audit.AuditAction;
import com.brewpay.audit.AuditLog;
import com.brewpay.audit.AuditTargetType;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

  private final OrganizationRepository organizations;
  private final UserRepository users;
  private final SessionRepository sessions;
  private final AuditLog auditLog;
  private final PasswordEncoder passwordEncoder;
  private final SessionPolicy policy;
  private final Clock clock;
  private final String dummyHash;

  public SessionService(
      OrganizationRepository organizations,
      UserRepository users,
      SessionRepository sessions,
      AuditLog auditLog,
      PasswordEncoder passwordEncoder,
      SessionPolicy policy,
      Clock clock) {
    this.organizations = organizations;
    this.users = users;
    this.sessions = sessions;
    this.auditLog = auditLog;
    this.passwordEncoder = passwordEncoder;
    this.policy = policy;
    this.clock = clock;
    // Keep an unknown email on the same bcrypt cost as a wrong password.
    this.dummyHash = passwordEncoder.encode("brewpay-invalid-password");
  }

  @Transactional
  public SessionResponse login(CreateSessionRequest request) {
    UUID organizationId = request.organizationId();
    String email = request.email().trim().toLowerCase(Locale.ROOT);
    Optional<UserAccount> found = users.findByOrganizationIdAndEmail(organizationId, email);
    UserAccount account = found.orElse(null);
    boolean passwordMatches =
        account != null && passwordEncoder.matches(request.password(), account.getPasswordHash());
    if (!passwordMatches) {
      if (account == null) {
        passwordEncoder.matches(request.password(), dummyHash);
      }
      if (organizations.findById(organizationId).isPresent()) {
        UUID actorId = account == null ? null : account.getId();
        auditLog.recordInNewTransaction(
            organizationId, actorId, AuditAction.SESSION_REJECTED, AuditTargetType.USER, actorId);
      }
      throw new PlatformException(ErrorCode.UNAUTHORIZED, "Email or password is wrong.");
    }

    Instant createdAt = clock.instant();
    String token = SessionTokens.newToken();
    UserSession session =
        new UserSession(
            UUID.randomUUID(),
            organizationId,
            account.getId(),
            SessionTokens.hash(token),
            createdAt,
            createdAt.plus(policy.ttl()),
            null);
    sessions.saveAndFlush(session);
    auditLog.record(
        organizationId,
        account.getId(),
        AuditAction.SESSION_CREATED,
        AuditTargetType.SESSION,
        session.getId());
    return new SessionResponse(
        session.getId(), organizationId, account.getId(), token, session.getExpiresAt());
  }

  @Transactional(readOnly = true)
  public Actor authenticate(String token) {
    UserSession session =
        sessions.findByTokenHash(SessionTokens.hash(token)).orElseThrow(SessionService::invalid);
    if (session.getRevokedAt() != null || !session.getExpiresAt().isAfter(clock.instant())) {
      throw invalid();
    }
    UserAccount user =
        users
            .findByIdAndOrganizationId(session.getUserId(), session.getOrganizationId())
            .orElseThrow(SessionService::invalid);
    return new Actor(session.getId(), user.getId(), user.getOrganizationId(), user.getRole());
  }

  @Transactional
  public void revokeCurrent() {
    Actor actor = ActorContext.require();
    UserSession session =
        sessions
            .findByIdAndOrganizationId(actor.sessionId(), actor.organizationId())
            .orElseThrow(SessionService::invalid);
    session.revoke(clock.instant());
    sessions.saveAndFlush(session);
    auditLog.record(
        actor.organizationId(),
        actor.userId(),
        AuditAction.SESSION_REVOKED,
        AuditTargetType.SESSION,
        session.getId());
  }

  private static PlatformException invalid() {
    return new PlatformException(ErrorCode.UNAUTHORIZED, "Session is invalid.");
  }
}
