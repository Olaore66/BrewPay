package com.brewpay.payments;

import java.util.UUID;

public record NameEnquiryResponse(
    UUID id,
    UUID organizationId,
    String destinationAccountNumber,
    String destinationBankCode,
    String accountName) {

  static NameEnquiryResponse from(AccountNameEnquiry enquiry) {
    return new NameEnquiryResponse(
        enquiry.getId(),
        enquiry.getOrganizationId(),
        enquiry.getDestinationAccountNumber(),
        enquiry.getDestinationBankCode(),
        enquiry.getAccountName());
  }
}
