package com.brewpay.banking;

/** A provider answer. A timeout is not one of these; the provider throws instead. */
public record TransferInstruction(String providerReference, ProviderPaymentStatus status) {}
