package com.brewpay.identity;

import java.time.Duration;

public record SessionPolicy(Duration ttl) {}
