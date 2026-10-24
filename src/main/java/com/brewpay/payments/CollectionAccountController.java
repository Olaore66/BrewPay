package com.brewpay.payments;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/collection-accounts")
public class CollectionAccountController {

  private final CollectionService collections;

  public CollectionAccountController(CollectionService collections) {
    this.collections = collections;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public CollectionAccountResponse open(@Valid @RequestBody OpenCollectionAccountRequest request) {
    return collections.open(request);
  }

  @GetMapping
  public List<CollectionAccountResponse> list() {
    return collections.listAccounts();
  }
}
