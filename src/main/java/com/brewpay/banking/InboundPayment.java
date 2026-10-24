package com.brewpay.banking;

/** A provider payment after the adapter has checked the signature and read the payload. */
public record InboundPayment(
    String eventId, String accountReference, long amountKobo, ProviderPaymentStatus status) {}
