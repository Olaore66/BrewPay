package com.brewpay.banking;

import java.util.UUID;

/** Business profile sent to the provider. The provider decides the verdict. */
public record KycApplication(
    UUID organizationId,
    String legalName,
    String tradingName,
    String businessType,
    String registrationNumber,
    String addressLine,
    String state,
    String category) {}
