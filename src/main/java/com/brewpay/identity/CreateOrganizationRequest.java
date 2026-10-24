package com.brewpay.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateOrganizationRequest(
    @NotBlank @Size(max = 160) String name, @NotNull @Valid Owner owner) {

  public record Owner(
      @NotBlank @Size(max = 80) String firstName,
      @NotBlank @Size(max = 80) String lastName,
      @NotBlank @Email @Size(max = 320) String email) {}
}
