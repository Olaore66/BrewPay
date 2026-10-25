package com.brewpay.identity;

/** Trimmed business profile. The registration number is null for a sole proprietor who has none. */
record KybProfile(
    String tradingName,
    BusinessType businessType,
    String registrationNumber,
    String addressLine,
    String state,
    String category) {}
