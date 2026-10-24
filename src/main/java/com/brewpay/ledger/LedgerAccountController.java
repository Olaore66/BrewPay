package com.brewpay.ledger;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger/accounts")
public class LedgerAccountController {

  private final LedgerService ledger;

  public LedgerAccountController(LedgerService ledger) {
    this.ledger = ledger;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public AccountResponse create(@Valid @RequestBody CreateAccountRequest request) {
    return ledger.createAccount(request);
  }

  @GetMapping
  public List<AccountResponse> list() {
    return ledger.listAccounts();
  }

  @GetMapping("/{id}")
  public AccountResponse get(@PathVariable UUID id) {
    return ledger.getAccount(id);
  }
}
