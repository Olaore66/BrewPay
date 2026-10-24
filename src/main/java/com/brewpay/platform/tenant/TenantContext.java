package com.brewpay.platform.tenant;

import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.util.UUID;

/**
 * The organization bound to the current request.
 *
 * <p>The session interceptor copies this from the authenticated session. {@code X-Organization-Id}
 * is ignored. Repositories must still scope every child read by this id.
 */
public final class TenantContext {

  /** Old client header. The server does not read it. */
  public static final String HEADER = "X-Organization-Id";

  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(UUID organizationId) {
    CURRENT.set(organizationId);
  }

  public static UUID require() {
    UUID organizationId = CURRENT.get();
    if (organizationId == null) {
      throw new PlatformException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
    }
    return organizationId;
  }

  public static void clear() {
    CURRENT.remove();
  }
}
