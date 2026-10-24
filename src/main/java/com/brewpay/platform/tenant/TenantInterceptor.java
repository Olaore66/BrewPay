package com.brewpay.platform.tenant;

import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class TenantInterceptor implements HandlerInterceptor {

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    String uri = request.getRequestURI();
    if (!uri.equals("/api/v1/users") && !uri.startsWith("/api/v1/users/")) {
      return true;
    }
    String raw = request.getHeader(TenantContext.HEADER);
    if (raw == null || raw.isBlank()) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Organization context is required.");
    }
    try {
      TenantContext.set(UUID.fromString(raw));
    } catch (IllegalArgumentException exception) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Organization context is invalid.");
    }
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    TenantContext.clear();
  }
}
