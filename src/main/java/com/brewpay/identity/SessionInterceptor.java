package com.brewpay.identity;

import com.brewpay.access.Actor;
import com.brewpay.access.ActorContext;
import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import com.brewpay.platform.tenant.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class SessionInterceptor implements HandlerInterceptor {

  static final String AUTHORIZATION = "Authorization";

  private final SessionService sessions;

  public SessionInterceptor(SessionService sessions) {
    this.sessions = sessions;
  }

  @Override
  public boolean preHandle(
      HttpServletRequest request, HttpServletResponse response, Object handler) {
    if (isPublic(request) || !request.getRequestURI().startsWith("/api/v1/")) {
      return true;
    }
    String header = request.getHeader(AUTHORIZATION);
    if (header == null || !header.startsWith("Bearer ")) {
      throw new PlatformException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
    }
    String token = header.substring("Bearer ".length()).trim();
    if (token.isEmpty()) {
      throw new PlatformException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
    }
    Actor actor = sessions.authenticate(token);
    ActorContext.set(actor);
    TenantContext.set(actor.organizationId());
    return true;
  }

  @Override
  public void afterCompletion(
      HttpServletRequest request,
      HttpServletResponse response,
      Object handler,
      Exception exception) {
    ActorContext.clear();
    TenantContext.clear();
  }

  private static boolean isPublic(HttpServletRequest request) {
    String uri = request.getRequestURI();
    String method = request.getMethod();
    if ("GET".equals(method) && "/api/v1/health".equals(uri)) {
      return true;
    }
    if ("POST".equals(method) && "/api/v1/organizations".equals(uri)) {
      return true;
    }
    if ("POST".equals(method) && "/api/v1/sessions".equals(uri)) {
      return true;
    }
    return "POST".equals(method) && "/api/v1/webhooks/fake-bank".equals(uri);
  }
}
