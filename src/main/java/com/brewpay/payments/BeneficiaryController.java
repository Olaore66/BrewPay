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
@RequestMapping("/api/v1/beneficiaries")
public class BeneficiaryController {

  private final BeneficiaryService beneficiaries;

  public BeneficiaryController(BeneficiaryService beneficiaries) {
    this.beneficiaries = beneficiaries;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public BeneficiaryResponse save(@Valid @RequestBody SaveBeneficiaryRequest request) {
    return beneficiaries.save(request);
  }

  @GetMapping
  public List<BeneficiaryResponse> list() {
    return beneficiaries.list();
  }

  @GetMapping("/{id}")
  public BeneficiaryResponse get(@PathVariable UUID id) {
    return beneficiaries.get(id);
  }
}
