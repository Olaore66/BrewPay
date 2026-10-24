package com.brewpay.ledger;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record PostJournalRequest(
    @NotBlank @Size(max = 200) String description,
    @NotNull @Size(min = 2) @Valid List<JournalLineRequest> lines) {}
