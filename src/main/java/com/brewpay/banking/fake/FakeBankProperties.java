package com.brewpay.banking.fake;

/** Local secret for the fake bank. This is not a Kolomoni credential. */
public record FakeBankProperties(String webhookSecret) {}
