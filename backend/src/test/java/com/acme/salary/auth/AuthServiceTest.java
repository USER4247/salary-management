package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.salary.auth.AuthService.Tokens;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.web.server.ResponseStatusException;

/** Unit tests: no Spring, no database, just the logic. The repositories are mocks, the clock is frozen. */
class AuthServiceTest {

    private static final String PASSWORD = "correct-horse";

    private final Instant now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);   // low cost = fast tests
    private final SecretKey key = new SecretKeySpec(
            "unit-test-secret-that-is-at-least-32-chars".getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    private final NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private AuthService service;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("maya@acme.test", encoder.encode(PASSWORD), "Maya", Role.COUNTRY_HR, "IN", now);
        when(users.findByEmailIgnoreCase("maya@acme.test")).thenReturn(Optional.of(user));
        when(users.findById(any())).thenReturn(Optional.of(user));
        service = new AuthService(users, refreshTokens, encoder, new NimbusJwtEncoder(new ImmutableSecret<>(key)),
                clock, Duration.ofMinutes(15), Duration.ofDays(7));
    }

    // ----- login -----

    @Test
    void loginGivesAnAccessTokenWithIdentityClaimsAndAnExpiry() {
        Tokens tokens = service.login("maya@acme.test", PASSWORD);

        Jwt jwt = jwtDecoder.decode(tokens.accessToken());
        assertThat(jwt.getClaimAsString("email")).isEqualTo("maya@acme.test");
        assertThat(jwt.getClaimAsString("role")).isEqualTo("COUNTRY_HR");
        assertThat(jwt.getClaimAsString("countryScope")).isEqualTo("IN");
        assertThat(tokens.accessExpiresAt()).isEqualTo(now.plus(Duration.ofMinutes(15)));
        assertThat(jwt.getExpiresAt()).isEqualTo(tokens.accessExpiresAt());
        assertThat(tokens.refreshExpiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
    }

    @Test
    void loginStoresOnlyAHashOfTheRefreshToken() {
        Tokens tokens = service.login("maya@acme.test", PASSWORD);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isNotEqualTo(tokens.refreshToken()).hasSize(64);
    }

    @Test
    void wrongPasswordUnknownEmailAndDisabledUserAllFailTheSameWay() {
        when(users.findByEmailIgnoreCase("ghost@acme.test")).thenReturn(Optional.empty());
        User disabled = new User("off@acme.test", encoder.encode(PASSWORD), "Off", Role.HR_VIEWER, null, now);
        disabled.setEnabled(false);
        when(users.findByEmailIgnoreCase("off@acme.test")).thenReturn(Optional.of(disabled));

        assertUnauthorized(() -> service.login("maya@acme.test", "wrong"), "Invalid credentials");
        assertUnauthorized(() -> service.login("ghost@acme.test", PASSWORD), "Invalid credentials");
        assertUnauthorized(() -> service.login("off@acme.test", PASSWORD), "Invalid credentials");
        verify(refreshTokens, never()).save(any());
    }

    // ----- refresh -----

    @Test
    void refreshDeletesTheUsedTokenAndIssuesANewOne() {
        Tokens first = service.login("maya@acme.test", PASSWORD);
        RefreshToken stored = storedTokenFor(first.refreshToken());

        Tokens second = service.refresh(first.refreshToken());

        verify(refreshTokens).delete(stored);
        assertThat(second.refreshToken()).isNotEqualTo(first.refreshToken());
    }

    @Test
    void refreshWithAnUnknownOrUsedTokenIsRejected() {
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertUnauthorized(() -> service.refresh("garbage"), "Invalid session");
        assertUnauthorized(() -> service.refresh(null), "Invalid session");
    }

    @Test
    void refreshWithAnExpiredTokenIsRejected() {
        Tokens first = service.login("maya@acme.test", PASSWORD);
        RefreshToken expired = new RefreshToken(1L, storedTokenFor(first.refreshToken()).getTokenHash(), now.minusSeconds(1), now);
        when(refreshTokens.findByTokenHash(expired.getTokenHash())).thenReturn(Optional.of(expired));

        assertUnauthorized(() -> service.refresh(first.refreshToken()), "Invalid session");
    }

    @Test
    void refreshIsRejectedWhenTheUserWasDisabledMeanwhile() {
        Tokens first = service.login("maya@acme.test", PASSWORD);
        storedTokenFor(first.refreshToken());
        user.setEnabled(false);

        assertUnauthorized(() -> service.refresh(first.refreshToken()), "Invalid session");
    }

    // ----- logout -----

    @Test
    void logoutDeletesTheTokenAndIgnoresUnknownOnes() {
        Tokens first = service.login("maya@acme.test", PASSWORD);
        RefreshToken stored = storedTokenFor(first.refreshToken());
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());
        when(refreshTokens.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));

        service.logout(first.refreshToken());
        service.logout("unknown");
        service.logout(null);

        verify(refreshTokens).delete(stored);
    }

    // ----- helpers -----

    /** The refresh token the service saved at login, as the database would return it. */
    private RefreshToken storedTokenFor(String rawToken) {
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens, org.mockito.Mockito.atLeastOnce()).save(saved.capture());
        RefreshToken stored = saved.getAllValues().get(0);
        when(refreshTokens.findByTokenHash(stored.getTokenHash())).thenReturn(Optional.of(stored));
        return stored;
    }

    private static void assertUnauthorized(Runnable call, String message) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(e.getReason()).isEqualTo(message);
                });
    }
}
