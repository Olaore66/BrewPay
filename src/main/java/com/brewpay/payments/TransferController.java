package com.brewpay.payments;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
public class TransferController {

  private final TransferService transfers;

  public TransferController(TransferService transfers) {
    this.transfers = transfers;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public TransferResponse submit(
      @RequestHeader(value = IdempotencyKeys.HEADER, required = false) String idempotencyKey,
      @Valid @RequestBody SubmitTransferRequest request) {
    return transfers.submit(idempotencyKey, request);
  }

  @GetMapping
  public List<TransferResponse> list() {
    return transfers.list();
  }

  @GetMapping("/{id}")
  public TransferResponse get(@PathVariable UUID id) {
    return transfers.get(id);
  }
}
