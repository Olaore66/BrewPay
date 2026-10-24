package com.brewpay.banking.fake;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FakeBankConfig {

  @Bean
  FakeBankProperties fakeBankProperties(
      @Value("${brewpay.banking.fake.webhook-secret}") String webhookSecret) {
    if (webhookSecret == null || webhookSecret.isBlank()) {
      throw new IllegalStateException("brewpay.banking.fake.webhook-secret is required");
    }
    return new FakeBankProperties(webhookSecret);
  }
}
