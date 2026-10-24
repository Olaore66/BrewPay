package com.brewpay.access;

import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;

/** The only permission rule in this slice: the owner adds users and reads the audit. */
public final class Permissions {

  private Permissions() {}

  public static void requireOwner(Role role) {
    if (role != Role.OWNER) {
      throw new PlatformException(
          ErrorCode.FORBIDDEN, "You do not have permission to perform this action.");
    }
  }
}
