package com.brewpay.payments;

import com.brewpay.ledger.JournalResponse;
import com.brewpay.ledger.PostJournalRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ledger/journals")
public class IdempotentJournalController {

  private final IdempotentJournalPost posts;

  public IdempotentJournalController(IdempotentJournalPost posts) {
    this.posts = posts;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public JournalResponse post(
      @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
      @Valid @RequestBody PostJournalRequest request) {
    return posts.post(idempotencyKey, request);
  }
}
