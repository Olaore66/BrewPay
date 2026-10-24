package com.brewpay.identity;

import java.util.UUID;

public record OrganizationResponse(UUID id, String name, String status, UserResponse owner) {

  static OrganizationResponse from(Organization organization, UserAccount owner) {
    return new OrganizationResponse(
        organization.getId(),
        organization.getName(),
        organization.getStatus().name(),
        UserResponse.from(owner));
  }
}
