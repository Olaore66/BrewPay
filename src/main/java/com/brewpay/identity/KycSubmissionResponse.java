package com.brewpay.identity;

import java.util.UUID;

public record KycSubmissionResponse(
    UUID id,
    UUID organizationId,
    UUID userId,
    String dateOfBirth,
    String phone,
    String identificationNumber,
    String kycStatus) {

  static KycSubmissionResponse from(KycSubmission submission) {
    return new KycSubmissionResponse(
        submission.getId(),
        submission.getOrganizationId(),
        submission.getUserId(),
        submission.getDateOfBirth().toString(),
        submission.getPhone(),
        submission.getIdentificationNumber(),
        submission.getKycStatus().name());
  }
}
