package com.brewpay.platform.tenant;

import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;
import java.util.UUID;

/**
 * The organization bound to the current request.
 *
 * <p>Slice 3 will set this from the authenticated session. Until then the HTTP layer copies {@code
 * X-Organization-Id} into this context. Repositories must still scope every child read by this id.
 */
public final class TenantContext {

  public static final String HEADER = "X-Organization-Id";

  private static final ThreadLocal<UUID> CURRENT = new ThreadLocal<>();

  private TenantContext() {}

  public static void set(UUID organizationId) {
    CURRENT.set(organizationId);
  }

  public static UUID require() {
    UUID organizationId = CURRENT.get();
    if (organizationId == null) {
      throw new PlatformException(ErrorCode.BAD_REQUEST, "Organization context is required.");
    }
    return organizationId;
  }

  public static void clear() {
    CURRENT.remove();
  }
}
