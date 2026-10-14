package com.acme.salary.auth.session;

import com.acme.salary.auth.AuthProperties;
import com.acme.salary.auth.token.AccessTokenService;
import com.acme.salary.auth.user.User;
import com.acme.salary.auth.user.UserRepository;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Sign-in, refresh-token rotation with reuse detection, and sign-out. */
@Service
public class SessionService {

    private static final String INVALID_CREDENTIALS = "Invalid credentials";
    private static final String INVALID_SESSION = "Invalid session";

    private final SecureRandom random = new SecureRandom();
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenService accessTokens;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;
    private final String dummyHash;

    public SessionService(
            UserRepository users,
            RefreshTokenRepository refreshTokens,
            AccessTokenService accessTokens,
            PasswordEncoder passwordEncoder,
            AuthProperties properties,
            Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.accessTokens = accessTokens;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public Session login(String email, String password) {
        User user = users.findByEmailIgnoreCase(email == null ? "" : email.trim()).orElse(null);
        // Always run one hash comparison so unknown users and wrong passwords take similar time.
        boolean passwordOk = passwordEncoder.matches(password == null ? "" : password,
                user == null ? dummyHash : user.getPasswordHash());
        if (user == null || !passwordOk || !user.isEnabled()) {
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }
        return issue(user, UUID.randomUUID().toString());
    }

    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public Session refresh(String rawToken) {
        Instant now = clock.instant();
        RefreshToken current = find(rawToken).orElseThrow(() -> new AuthenticationFailedException(INVALID_SESSION));

        if (current.isSpent()) {
            // A rotated or revoked token came back: assume it was stolen and kill the whole chain.
            refreshTokens.revokeFamily(current.getFamilyId(), now);
            throw new AuthenticationFailedException(INVALID_SESSION);
        }
        User user = users.findById(current.getUserId()).filter(User::isEnabled).orElse(null);
        if (current.isExpired(now) || user == null) {
            throw new AuthenticationFailedException(INVALID_SESSION);
        }

        current.markUsed(now); // managed entity: persisted by the surrounding transaction
        return issue(user, current.getFamilyId());
    }

    @Transactional
    public void logout(String rawToken) {
        find(rawToken).ifPresent(token -> refreshTokens.revokeFamily(token.getFamilyId(), clock.instant()));
    }

    private java.util.Optional<RefreshToken> find(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return java.util.Optional.empty();
        }
        return refreshTokens.findByTokenHash(TokenHasher.sha256(rawToken));
    }

    private Session issue(User user, String familyId) {
        Instant now = clock.instant();
        String raw = newRawToken();
        Instant expiresAt = now.plus(properties.refreshTtl());
        refreshTokens.save(new RefreshToken(user.getId(), familyId, TokenHasher.sha256(raw), expiresAt, now));
        return new Session(user, accessTokens.issue(user), new Session.IssuedRefreshToken(raw, expiresAt));
    }

    private String newRawToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
