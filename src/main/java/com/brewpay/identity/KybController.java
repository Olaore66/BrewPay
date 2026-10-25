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
@RequestMapping("/api/v1/kyb-submissions")
public class KybController {

  private final KybService submissions;

  public KybController(KybService submissions) {
    this.submissions = submissions;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public KybSubmissionResponse submit(@Valid @RequestBody SubmitKybRequest request) {
    return submissions.submit(request);
  }

  @GetMapping
  public List<KybSubmissionResponse> list() {
    return submissions.list();
  }

  @GetMapping("/{id}")
  public KybSubmissionResponse get(@PathVariable UUID id) {
    return submissions.get(id);
  }
}
