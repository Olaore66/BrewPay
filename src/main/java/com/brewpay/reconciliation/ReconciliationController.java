package com.brewpay.reconciliation;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReconciliationController {

  private final ReconciliationService reconciliation;

  public ReconciliationController(ReconciliationService reconciliation) {
    this.reconciliation = reconciliation;
  }

  @PostMapping("/api/v1/reconciliation-runs")
  @ResponseStatus(HttpStatus.CREATED)
  public ReconciliationRunResponse run() {
    return reconciliation.run();
  }

  @GetMapping("/api/v1/reconciliation-findings")
  public List<ReconciliationFindingResponse> list() {
    return reconciliation.list();
  }

  @GetMapping("/api/v1/reconciliation-findings/{id}")
  public ReconciliationFindingResponse get(@PathVariable UUID id) {
    return reconciliation.get(id);
  }
}
