package com.brewpay.banking;

import java.util.UUID;

/** Person profile sent to the provider. The provider decides the verdict. */
public record PersonKycApplication(
    UUID organizationId,
    UUID userId,
    String legalName,
    String dateOfBirth,
    String phone,
    String identificationNumber) {}
