package com.brewpay.reconciliation;

import java.util.List;
import java.util.UUID;

public record ReconciliationRunResponse(UUID id, List<ReconciliationFindingResponse> findings) {}
