package com.brewpay.banking;

/** What the platform asks a bank to pay out. The idempotency key lets the provider dedupe. */
public record TransferRequest(
    String idempotencyKey,
    long amountKobo,
    String destinationAccountNumber,
    String destinationBankCode) {}
