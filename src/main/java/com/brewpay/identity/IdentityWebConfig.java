package com.brewpay.identity;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class IdentityWebConfig implements WebMvcConfigurer {

  private final SessionInterceptor sessionInterceptor;
  private final MerchantClearance merchantClearance;

  public IdentityWebConfig(
      SessionInterceptor sessionInterceptor, MerchantClearance merchantClearance) {
    this.sessionInterceptor = sessionInterceptor;
    this.merchantClearance = merchantClearance;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(sessionInterceptor);
    registry.addInterceptor(merchantClearance);
  }
}
