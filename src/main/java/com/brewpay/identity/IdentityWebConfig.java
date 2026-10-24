package com.brewpay.identity;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class IdentityWebConfig implements WebMvcConfigurer {

  private final SessionInterceptor sessionInterceptor;

  public IdentityWebConfig(SessionInterceptor sessionInterceptor) {
    this.sessionInterceptor = sessionInterceptor;
  }

  @Override
  public void addInterceptors(InterceptorRegistry registry) {
    registry.addInterceptor(sessionInterceptor);
  }
}
