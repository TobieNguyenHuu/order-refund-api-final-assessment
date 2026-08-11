package com.assessment.orderapi.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for the security.jwt.* properties.
 * Preferred over @Value field injection: the values are validated at startup,
 * are visible in the constructor signature, and can be supplied directly in
 * unit tests without a Spring context.
 */
@ConfigurationProperties(prefix = "security.jwt")
public record JwtProperties(String signerKey, long validDuration) {
}
