package com.brewpay.ledger;

import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger/journals")
public class LedgerJournalController {

  private final LedgerService ledger;

  public LedgerJournalController(LedgerService ledger) {
    this.ledger = ledger;
  }

  @GetMapping
  public List<JournalResponse> list() {
    return ledger.listJournals();
  }

  @GetMapping("/{id}")
  public JournalResponse get(@PathVariable UUID id) {
    return ledger.getJournal(id);
  }

  @PostMapping("/{id}/reversals")
  @ResponseStatus(HttpStatus.CREATED)
  public JournalResponse reverse(@PathVariable UUID id) {
    return ledger.reverse(id);
  }
}
