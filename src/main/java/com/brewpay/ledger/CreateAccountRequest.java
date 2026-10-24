package com.brewpay.ledger;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateAccountRequest(
    @NotBlank @Size(max = 32) String code,
    @NotBlank @Size(max = 160) String name,
    @NotNull AccountType type) {}
