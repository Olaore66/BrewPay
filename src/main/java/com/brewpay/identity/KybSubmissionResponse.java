package com.brewpay.identity;

import java.util.UUID;

public record KybSubmissionResponse(
    UUID id,
    UUID organizationId,
    String tradingName,
    String businessType,
    String registrationNumber,
    String addressLine,
    String state,
    String category,
    String kycStatus,
    String organizationStatus) {

  static KybSubmissionResponse from(KybSubmission submission, Organization organization) {
    return new KybSubmissionResponse(
        submission.getId(),
        submission.getOrganizationId(),
        submission.getTradingName(),
        submission.getBusinessType().name(),
        submission.getRegistrationNumber(),
        submission.getAddressLine(),
        submission.getState(),
        submission.getCategory(),
        submission.getKycStatus().name(),
        organization.getStatus().name());
  }
}
