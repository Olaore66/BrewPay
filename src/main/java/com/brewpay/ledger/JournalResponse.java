package com.brewpay.ledger;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record JournalResponse(
    UUID id,
    UUID organizationId,
    String description,
    String currency,
    UUID reversesJournalId,
    Instant createdAt,
    List<JournalLineResponse> lines) {

  static JournalResponse from(Journal journal, List<JournalLine> lines) {
    return new JournalResponse(
        journal.getId(),
        journal.getOrganizationId(),
        journal.getDescription(),
        journal.getCurrency(),
        journal.getReversesJournalId(),
        journal.getCreatedAt(),
        lines.stream().map(JournalLineResponse::from).toList());
  }
}
