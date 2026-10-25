package com.brewpay.identity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubmitKybRequest(
    @NotBlank @Size(max = 160) String tradingName,
    @NotBlank String businessType,
    @Size(max = 40) String registrationNumber,
    @NotBlank @Size(max = 200) String addressLine,
    @NotBlank @Size(max = 40) String state,
    @NotBlank @Size(max = 80) String category) {}
