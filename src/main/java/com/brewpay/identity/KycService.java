package com.brewpay.identity;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.banking.KycProvider;
import com.brewpay.banking.KycVerdict;
import com.brewpay.banking.PersonKycApplication;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KycService {

  private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{8,15}$");
  private static final Pattern IDENTIFICATION = Pattern.compile("^[0-9]{11}$");

  private final UserRepository users;
  private final KycSubmissionRepository submissions;
  private final KycProvider provider;
  private final KycClaim claims;
  private final Clock clock;

  public KycService(
      UserRepository users,
      KycSubmissionRepository submissions,
      KycProvider provider,
      KycClaim claims,
      Clock clock) {
    this.users = users;
    this.submissions = submissions;
    this.provider = provider;
    this.claims = claims;
    this.clock = clock;
  }

  public KycSubmissionResponse submit(SubmitKycRequest request) {
    Actor actor = ActorContext.require();
    KycProfile profile = profile(request);
    UserAccount user = user(actor);
    if (user.getKycStatus() == KycStatus.APPROVED) {
      throw new PlatformException(ErrorCode.CONFLICT, "Identity is already verified.");
    }
    if (user.getKycStatus() == KycStatus.PENDING) {
      throw new PlatformException(
          ErrorCode.CONFLICT, "Identity verification is already in review.");
    }
    KycVerdict verdict =
        provider.reviewPerson(
            new PersonKycApplication(
                user.getOrganizationId(),
                user.getId(),
                user.getFirstName() + " " + user.getLastName(),
                profile.dateOfBirth().toString(),
                profile.phone(),
                profile.identificationNumber()));
    return claims.store(actor, profile, toStatus(verdict));
  }

  @Transactional(readOnly = true)
  public KycSubmissionResponse get(UUID id) {
    Actor actor = ActorContext.require();
    return submissions
        .findByIdAndOrganizationIdAndUserId(id, actor.organizationId(), actor.userId())
        .map(KycSubmissionResponse::from)
        .orElseThrow(
            () -> new PlatformException(ErrorCode.NOT_FOUND, "KYC submission was not found."));
  }

  @Transactional(readOnly = true)
  public List<KycSubmissionResponse> list() {
    Actor actor = ActorContext.require();
    return submissions
        .findByOrganizationIdAndUserIdOrderByCreatedAtAsc(actor.organizationId(), actor.userId())
        .stream()
        .map(KycSubmissionResponse::from)
        .toList();
  }

  private UserAccount user(Actor actor) {
    return users
        .findByIdAndOrganizationId(actor.userId(), actor.organizationId())
        .orElseThrow(() -> new PlatformException(ErrorCode.NOT_FOUND, "User was not found."));
  }

  private static KycStatus toStatus(KycVerdict verdict) {
    return switch (verdict) {
      case PENDING -> KycStatus.PENDING;
      case APPROVED -> KycStatus.APPROVED;
      case REJECTED -> KycStatus.REJECTED;
    };
  }

  private KycProfile profile(SubmitKycRequest request) {
    return new KycProfile(
        dateOfBirth(request.dateOfBirth()),
        phone(request.phone()),
        identification(request.identificationNumber()));
  }

  private LocalDate dateOfBirth(String value) {
    String trimmed = value == null ? "" : value.trim();
    LocalDate date;
    try {
      date = LocalDate.parse(trimmed);
    } catch (DateTimeException exception) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Date of birth is invalid.");
    }
    if (date.isAfter(LocalDate.now(clock))) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Date of birth is invalid.");
    }
    return date;
  }

  private static String phone(String value) {
    String phone = value == null ? "" : value.trim();
    if (!PHONE.matcher(phone).matches()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Phone number is invalid.");
    }
    return phone;
  }

  private static String identification(String value) {
    String identification = value == null ? "" : value.trim();
    if (!IDENTIFICATION.matcher(identification).matches()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Identification number is invalid.");
    }
    return identification;
  }
}
