package com.acme.salary.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Auth settings, bound from {@code app.auth.*}. The signing secret must come from the environment. */
@ConfigurationProperties(prefix = "app.auth")
public record AuthProperties(
        String jwtSecret, Duration accessTtl, Duration refreshTtl, String issuer, boolean cookieSecure) {

    private static final int MIN_SECRET_BYTES = 32;

    public AuthProperties {
        if (jwtSecret == null || jwtSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "app.auth.jwt-secret (JWT_SECRET) must be set to at least " + MIN_SECRET_BYTES + " bytes");
        }
    }
}
