package com.acme.salary.auth.token;

import static org.assertj.core.api.Assertions.assertThat;

import com.acme.salary.auth.AuthProperties;
import com.acme.salary.auth.ability.Role;
import com.acme.salary.auth.user.User;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class AccessTokenServiceTest {

    private static final String SECRET = "unit-test-secret-that-is-at-least-32-bytes-long";

    private final Instant now = Instant.now();
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final AuthProperties properties =
            new AuthProperties(SECRET, Duration.ofMinutes(15), Duration.ofDays(7), "salary-management", true);
    private final SecretKey key = new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final AccessTokenService service =
            new AccessTokenService(new NimbusJwtEncoder(new ImmutableSecret<>(key)), properties, clock);
    private final NimbusJwtDecoder decoder =
            NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

    @Test
    void tokenCarriesIdentityAndCustomClaims() {
        User user = new User("asha@acme.test", "hash", "Asha", Role.COUNTRY_HR, "IN", now);

        AccessToken token = service.issue(user);
        Jwt jwt = decoder.decode(token.value());

        assertThat(jwt.getClaimAsString("iss")).isEqualTo("salary-management");
        assertThat(jwt.getClaimAsString("email")).isEqualTo("asha@acme.test");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("COUNTRY_HR");
        assertThat(jwt.getClaimAsString("countryScope")).isEqualTo("IN");
    }

    @Test
    void tokenExpiresAfterConfiguredLifetime() {
        User user = new User("a@acme.test", "hash", "A", Role.HR_ADMIN, null, now);

        AccessToken token = service.issue(user);

        assertThat(token.expiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
        assertThat(decoder.decode(token.value()).getExpiresAt()).isEqualTo(token.expiresAt());
    }

    @Test
    void scopeClaimIsOmittedWhenUserHasNoCountryScope() {
        User user = new User("a@acme.test", "hash", "A", Role.HR_ADMIN, null, now);

        Jwt jwt = decoder.decode(service.issue(user).value());

        assertThat(jwt.hasClaim("countryScope")).isFalse();
    }

    @Test
    void secretShorterThan32BytesIsRejected() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> new AuthProperties("too-short", Duration.ofMinutes(1), Duration.ofDays(1), "x", true))
                .isInstanceOf(IllegalStateException.class);
    }
}
