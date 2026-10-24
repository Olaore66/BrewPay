package com.brewpay.payments;

import jakarta.validation.constraints.NotNull;

public record NameEnquiryRequest(
    @NotNull String destinationAccountNumber, @NotNull String destinationBankCode) {}
