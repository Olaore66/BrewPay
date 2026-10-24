package com.brewpay.banking;

/** One line from a provider statement. The reference is the provider's id for that line. */
public record StatementLine(
    StatementSubject subject, String reference, long amountKobo, ProviderPaymentStatus status) {}
