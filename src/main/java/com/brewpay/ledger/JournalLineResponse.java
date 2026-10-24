package com.brewpay.ledger;

import java.util.UUID;

public record JournalLineResponse(UUID accountId, String direction, long amountKobo, int position) {

  static JournalLineResponse from(JournalLine line) {
    return new JournalLineResponse(
        line.getAccountId(), line.getDirection().name(), line.getAmountKobo(), line.getPosition());
  }
}
