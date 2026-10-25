package com.brewpay.identity;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SessionConfig {

  @Bean
  SessionPolicy sessionPolicy(@Value("${brewpay.session.ttl:PT12H}") Duration ttl) {
    if (ttl.isZero() || ttl.isNegative()) {
      throw new IllegalStateException("brewpay.session.ttl must be positive");
    }
    return new SessionPolicy(ttl);
  }

  @Bean
  PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  @Bean
  SessionInterceptor sessionInterceptor(SessionService sessions) {
    return new SessionInterceptor(sessions);
  }

  @Bean
  MerchantClearance merchantClearance(OrganizationRepository organizations, UserRepository users) {
    return new MerchantClearance(organizations, users);
  }
}
