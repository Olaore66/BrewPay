package com.brewpay.identity;

import java.time.Instant;
import java.util.UUID;

public record SessionResponse(
    UUID id, UUID organizationId, UUID userId, String token, Instant expiresAt) {}
