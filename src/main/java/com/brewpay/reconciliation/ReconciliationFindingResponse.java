package com.brewpay.reconciliation;

import java.util.UUID;

public record ReconciliationFindingResponse(
    UUID id,
    UUID organizationId,
    String code,
    String subjectType,
    UUID subjectId,
    String providerReference,
    String providerStatus,
    String ourStatus,
    Long providerAmountKobo,
    Long ourAmountKobo) {

  static ReconciliationFindingResponse from(ReconciliationFinding finding) {
    return new ReconciliationFindingResponse(
        finding.getId(),
        finding.getOrganizationId(),
        finding.getCode().name(),
        finding.getSubjectType().name(),
        finding.getSubjectId(),
        finding.getProviderReference(),
        name(finding.getProviderStatus()),
        name(finding.getOurStatus()),
        finding.getProviderAmountKobo(),
        finding.getOurAmountKobo());
  }

  private static String name(Enum<?> value) {
    return value == null ? null : value.name();
  }
}
