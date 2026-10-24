package com.brewpay.identity;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/organizations")
public class OrganizationController {

  private final OrganizationService organizations;

  public OrganizationController(OrganizationService organizations) {
    this.organizations = organizations;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public OrganizationResponse create(@Valid @RequestBody CreateOrganizationRequest request) {
    return organizations.create(request);
  }
}
