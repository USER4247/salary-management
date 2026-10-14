package com.acme.salary.auth.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.acme.salary.auth.AuthProperties;
import com.acme.salary.auth.ability.Role;
import com.acme.salary.auth.token.AccessToken;
import com.acme.salary.auth.token.AccessTokenService;
import com.acme.salary.auth.user.User;
import com.acme.salary.auth.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

class SessionServiceTest {

    private static final String PASSWORD = "correct-horse";

    private final Instant now = Instant.parse("2026-01-01T10:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private final AuthProperties properties = new AuthProperties(
            "unit-test-secret-that-is-at-least-32-bytes-long", Duration.ofMinutes(15), Duration.ofDays(7), "iss", true);

    private final UserRepository users = mock(UserRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final AccessTokenService accessTokens = mock(AccessTokenService.class);
    private SessionService service;
    private User user;

    @BeforeEach
    void setUp() {
        user = new User("hr@acme.test", encoder.encode(PASSWORD), "HR", Role.HR_ADMIN, null, now);
        when(users.findByEmailIgnoreCase("hr@acme.test")).thenReturn(Optional.of(user));
        when(users.findById(any())).thenReturn(Optional.of(user));
        when(accessTokens.issue(any())).thenReturn(new AccessToken("access-jwt", now.plus(Duration.ofMinutes(15))));
        when(refreshTokens.save(any(RefreshToken.class))).thenAnswer(call -> call.getArgument(0));
        service = new SessionService(users, refreshTokens, accessTokens, encoder, properties, clock);
    }

    @Test
    void loginIssuesAccessAndRefreshTokens() {
        Session session = service.login("hr@acme.test", PASSWORD);

        assertThat(session.access().value()).isEqualTo("access-jwt");
        assertThat(session.refresh().value()).isNotBlank();
        assertThat(session.refresh().expiresAt()).isEqualTo(now.plus(Duration.ofDays(7)));
    }

    @Test
    void loginStoresOnlyTheHashOfTheRefreshToken() {
        Session session = service.login("hr@acme.test", PASSWORD);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(TokenHasher.sha256(session.refresh().value()))
                .isNotEqualTo(session.refresh().value());
    }

    @Test
    void loginIsCaseInsensitiveOnEmailAndRejectsWrongPassword() {
        assertThatThrownBy(() -> service.login("hr@acme.test", "wrong"))
                .isInstanceOf(AuthenticationFailedException.class);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void loginRejectsUnknownUserWithTheSameError() {
        when(users.findByEmailIgnoreCase("nobody@acme.test")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("nobody@acme.test", PASSWORD))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage("Invalid credentials");
    }

    @Test
    void loginRejectsDisabledUser() {
        user.setEnabled(false);

        assertThatThrownBy(() -> service.login("hr@acme.test", PASSWORD))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void refreshRotatesTheTokenWithinTheSameFamily() {
        RefreshToken current = storedToken("raw-1", "family-1");

        Session next = service.refresh("raw-1");

        assertThat(current.getUsedAt()).isEqualTo(now);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokens).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo("family-1");
        assertThat(saved.getValue().getTokenHash()).isEqualTo(TokenHasher.sha256(next.refresh().value()));
        assertThat(next.refresh().value()).isNotEqualTo("raw-1");
    }

    @Test
    void reusingARotatedTokenRevokesTheWholeFamily() {
        RefreshToken current = storedToken("raw-1", "family-1");
        current.markUsed(now.minusSeconds(30));

        assertThatThrownBy(() -> service.refresh("raw-1")).isInstanceOf(AuthenticationFailedException.class);

        verify(refreshTokens).revokeFamily("family-1", now);
        verify(refreshTokens, never()).save(any());
    }

    @Test
    void expiredRefreshTokenIsRejected() {
        RefreshToken expired = new RefreshToken(1L, "family-1", TokenHasher.sha256("raw-old"), now.minusSeconds(1), now.minusSeconds(100));
        when(refreshTokens.findByTokenHash(TokenHasher.sha256("raw-old"))).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.refresh("raw-old")).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void unknownRefreshTokenIsRejected() {
        when(refreshTokens.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.refresh("garbage")).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void refreshIsRejectedWhenUserWasDisabledMeanwhile() {
        storedToken("raw-1", "family-1");
        user.setEnabled(false);

        assertThatThrownBy(() -> service.refresh("raw-1")).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void logoutRevokesTheFamilyAndIgnoresUnknownTokens() {
        storedToken("raw-1", "family-1");
        when(refreshTokens.findByTokenHash(TokenHasher.sha256("unknown"))).thenReturn(Optional.empty());

        service.logout("raw-1");
        service.logout("unknown");
        service.logout(null);

        verify(refreshTokens).revokeFamily("family-1", now);
    }

    private RefreshToken storedToken(String raw, String familyId) {
        RefreshToken token = new RefreshToken(1L, familyId, TokenHasher.sha256(raw), now.plus(Duration.ofDays(1)), now.minusSeconds(60));
        when(refreshTokens.findByTokenHash(TokenHasher.sha256(raw))).thenReturn(Optional.of(token));
        return token;
    }
}
