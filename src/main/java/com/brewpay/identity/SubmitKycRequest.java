package com.brewpay.identity;

import jakarta.validation.constraints.NotBlank;

public record SubmitKycRequest(
    @NotBlank String dateOfBirth, @NotBlank String phone, @NotBlank String identificationNumber) {}
