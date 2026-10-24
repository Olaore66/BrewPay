package com.brewpay.payments;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record SubmitTransferRequest(
    @NotNull UUID clearingAccountId,
    @NotNull UUID merchantAccountId,
    @NotNull Long amountKobo,
    @NotNull String destinationAccountNumber,
    @NotNull String destinationBankCode) {}
