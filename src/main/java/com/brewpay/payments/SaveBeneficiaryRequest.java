package com.brewpay.payments;

import jakarta.validation.constraints.NotNull;

public record SaveBeneficiaryRequest(
    @NotNull String destinationAccountNumber, @NotNull String destinationBankCode) {}
