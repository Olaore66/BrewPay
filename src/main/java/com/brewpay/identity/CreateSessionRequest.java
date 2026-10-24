package com.brewpay.identity;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateSessionRequest(
    @NotNull UUID organizationId,
    @NotBlank @Email @Size(max = 320) String email,
    @NotBlank @Size(max = 72) String password) {}
