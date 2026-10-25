package com.brewpay.identity;

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
@RequestMapping("/api/v1/kyc-submissions")
public class KycController {

  private final KycService submissions;

  public KycController(KycService submissions) {
    this.submissions = submissions;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public KycSubmissionResponse submit(@Valid @RequestBody SubmitKycRequest request) {
    return submissions.submit(request);
  }

  @GetMapping
  public List<KycSubmissionResponse> list() {
    return submissions.list();
  }

  @GetMapping("/{id}")
  public KycSubmissionResponse get(@PathVariable UUID id) {
    return submissions.get(id);
  }
}
