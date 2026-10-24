package com.brewpay.access;

import java.util.UUID;

/** The session bound to the current request. */
public record Actor(UUID sessionId, UUID userId, UUID organizationId, Role role) {}
