package com.brewpay.payments;

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
@RequestMapping("/api/v1/account-name-enquiries")
public class AccountNameEnquiryController {

  private final NameEnquiryService enquiries;

  public AccountNameEnquiryController(NameEnquiryService enquiries) {
    this.enquiries = enquiries;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public NameEnquiryResponse enquire(@Valid @RequestBody NameEnquiryRequest request) {
    return enquiries.enquire(request);
  }

  @GetMapping
  public List<NameEnquiryResponse> list() {
    return enquiries.list();
  }

  @GetMapping("/{id}")
  public NameEnquiryResponse get(@PathVariable UUID id) {
    return enquiries.get(id);
  }
}
