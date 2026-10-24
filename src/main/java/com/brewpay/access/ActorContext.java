package com.brewpay.access;

import com.brewpay.platform.error.ErrorCode;
import com.brewpay.platform.error.PlatformException;

/**
 * Who is calling. The session interceptor sets this. Repositories must not trust a client header.
 */
public final class ActorContext {

  private static final ThreadLocal<Actor> CURRENT = new ThreadLocal<>();

  private ActorContext() {}

  public static void set(Actor actor) {
    CURRENT.set(actor);
  }

  public static Actor require() {
    Actor actor = CURRENT.get();
    if (actor == null) {
      throw new PlatformException(ErrorCode.UNAUTHORIZED, "Authentication is required.");
    }
    return actor;
  }

  public static void clear() {
    CURRENT.remove();
  }
}
