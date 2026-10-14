package com.acme.salary.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * All the sign-in logic in one place: login, refresh and logout.
 *
 * <p>Two tokens are handed out on sign-in:
 * <ul>
 *   <li><b>access token</b>: a short-lived (minutes) signed JWT that proves who you are on every request;</li>
 *   <li><b>refresh token</b>: a long-lived (days) random string used only to get a new access token,
 *       so you are not asked for your password every 15 minutes.</li>
 * </ul>
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String ISSUER = "salary-management";

    /** Everything the controller needs after a successful login or refresh. */
    public record Tokens(User user, String accessToken, Instant accessExpiresAt,
                         String refreshToken, Instant refreshExpiresAt) {
    }

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final Clock clock;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();
    /** A real hash we compare against when the email is unknown (see login). */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder, Clock clock,
                       @Value("${app.auth.access-ttl}") Duration accessTtl,
                       @Value("${app.auth.refresh-ttl}") Duration refreshTtl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.clock = clock;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    /** Checks the email and password and, if they are right, signs the user in. */
    @Transactional
    public Tokens login(String email, String password) {
        log.info("Login attempt for {}", email);
        User user = users.findByEmailIgnoreCase(email.trim()).orElse(null);

        // Always do one password comparison, even for an unknown email. Otherwise unknown emails would answer
        // faster, and an attacker could use the timing to find out which emails exist.
        String hashToCheck = user != null ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(password, hashToCheck);

        if (user == null || !passwordMatches || !user.isEnabled()) {
            log.warn("Login failed for {}", email);
            // Same message for every failure, so the caller cannot tell what was wrong.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        log.info("Login ok for {} (role {})", email, user.getRole());
        return issueTokens(user);
    }

    /** Swaps a valid refresh token for a brand new pair of tokens. */
    @Transactional
    public Tokens refresh(String rawRefreshToken) {
        RefreshToken stored = findStored(rawRefreshToken);
        if (stored == null) {
            log.warn("Refresh rejected: unknown, already used or missing token");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }

        // A refresh token works once. Deleting it now means a stolen copy cannot be replayed afterwards.
        refreshTokens.delete(stored);

        User user = users.findById(stored.getUserId()).filter(User::isEnabled).orElse(null);
        if (stored.isExpired(clock.instant()) || user == null) {
            log.warn("Refresh rejected: token expired or user disabled");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session");
        }
        log.info("Refresh ok for {}", user.getEmail());
        return issueTokens(user);
    }

    /** Forgets the refresh token, so it can never be used again. Safe to call with nothing. */
    @Transactional
    public void logout(String rawRefreshToken) {
        RefreshToken stored = findStored(rawRefreshToken);
        if (stored != null) {
            refreshTokens.delete(stored);
            log.info("Logout: refresh token removed");
        }
    }

    private RefreshToken findStored(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return null;
        }
        return refreshTokens.findByTokenHash(sha256(rawRefreshToken)).orElse(null);
    }

    /** Creates a fresh access token (JWT) and refresh token for the user. */
    private Tokens issueTokens(User user) {
        // JWT times only have whole seconds, so round now; then the expiry we report equals the one in the token.
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant accessExpiresAt = now.plus(accessTtl);

        // The token says WHO you are (id, email, role, country), not what you may do.
        // What you may do is worked out on the server from the role, so changing a role takes effect at once.
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(accessExpiresAt)
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole().name());
        if (user.getCountryScope() != null) {
            claims.claim("countryScope", user.getCountryScope());
        }
        String accessToken = jwtEncoder
                .encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims.build()))
                .getTokenValue();

        // The refresh token is just 32 random bytes. We give the client the value and keep only its hash.
        byte[] randomBytes = new byte[32];
        random.nextBytes(randomBytes);
        String refreshToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        Instant refreshExpiresAt = now.plus(refreshTtl);
        refreshTokens.save(new RefreshToken(user.getId(), sha256(refreshToken), refreshExpiresAt, now));

        return new Tokens(user, accessToken, accessExpiresAt, refreshToken, refreshExpiresAt);
    }

    /** One-way fingerprint of a token. Fast SHA-256 is fine here because the input is long and random. */
    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available in Java", e);
        }
    }
}
