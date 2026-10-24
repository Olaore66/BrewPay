package com.brewpay.payments;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record OpenCollectionAccountRequest(
    @NotNull UUID clearingAccountId, @NotNull UUID merchantAccountId) {}
