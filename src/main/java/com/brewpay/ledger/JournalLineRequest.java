package com.brewpay.ledger;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record JournalLineRequest(
    @NotNull UUID accountId, @NotNull Direction direction, @NotNull @Positive Long amountKobo) {}
