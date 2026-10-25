package com.brewpay.identity;

import java.util.UUID;

public record OrganizationResponse(
    UUID id, String name, String status, String kycStatus, UserResponse owner) {

  static OrganizationResponse from(Organization organization, UserAccount owner) {
    return new OrganizationResponse(
        organization.getId(),
        organization.getName(),
        organization.getStatus().name(),
        organization.getKycStatus().name(),
        UserResponse.from(owner));
  }
}
