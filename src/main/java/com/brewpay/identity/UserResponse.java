package com.brewpay.identity;

import java.util.UUID;

public record UserResponse(
    UUID id,
    UUID organizationId,
    String firstName,
    String lastName,
    String email,
    String status,
    String kycStatus,
    String role) {

  static UserResponse from(UserAccount user) {
    return new UserResponse(
        user.getId(),
        user.getOrganizationId(),
        user.getFirstName(),
        user.getLastName(),
        user.getEmail(),
        user.getStatus().name(),
        user.getKycStatus().name(),
        user.getRole().name());
  }
}
