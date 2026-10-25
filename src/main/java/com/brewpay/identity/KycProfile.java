package com.brewpay.identity;

import java.time.LocalDate;

/** Trimmed person profile. The identification number is not written to the audit. */
record KycProfile(LocalDate dateOfBirth, String phone, String identificationNumber) {}
