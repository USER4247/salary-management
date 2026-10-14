package com.acme.salary.auth;

import com.acme.salary.auth.AuthService.Tokens;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

/**
 * The four sign-in endpoints. The tokens are sent to the browser only as HttpOnly cookies, so JavaScript
 * (and therefore any injected script) can never read them. The JSON body carries just the user and the rules.
 */
@RestController
@RequestMapping("/api/auth")
class AuthController {

    static final String ACCESS_COOKIE = "access_token";
    static final String REFRESH_COOKIE = "refresh_token";
    /** The refresh cookie is only sent to /api/auth/*, never to ordinary API calls. */
    private static final String REFRESH_COOKIE_PATH = "/api/auth";

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    record UserView(Long id, String email, String fullName, Role role, String countryScope) {
    }

    /** What the Angular app receives: who you are, when the access token ends, and what you may do (CASL rules). */
    record SessionResponse(UserView user, Instant accessExpiresAt, List<Map<String, Object>> rules) {
    }

    private final AuthService authService;
    private final AuthContext authContext;
    private final UserRepository users;
    private final Clock clock;
    private final boolean cookieSecure;

    AuthController(AuthService authService, AuthContext authContext, UserRepository users, Clock clock,
                   @Value("${app.auth.cookie-secure}") boolean cookieSecure) {
        this.authService = authService;
        this.authContext = authContext;
        this.users = users;
        this.clock = clock;
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/login")
    ResponseEntity<SessionResponse> login(@Valid @RequestBody LoginRequest request) {
        return signedIn(authService.login(request.email(), request.password()));
    }

    /** Called by the Angular app shortly before (or just after) the access token runs out. */
    @PostMapping("/refresh")
    ResponseEntity<SessionResponse> refresh(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        return signedIn(authService.refresh(refreshToken));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = REFRESH_COOKIE, required = false) String refreshToken) {
        authService.logout(refreshToken);
        // Sending a cookie with the same name and an age of zero tells the browser to delete it.
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, "", "/", Duration.ZERO).toString());
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, "", REFRESH_COOKIE_PATH, Duration.ZERO).toString());
        return ResponseEntity.noContent().headers(headers).build();
    }

    /** Who am I? Used when the page loads. Does not issue or change any token. */
    @GetMapping("/me")
    SessionResponse me() {
        Jwt jwt = authContext.currentJwt();
        User user = users.findById(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid session"));
        return body(user, jwt.getExpiresAt());
    }

    private ResponseEntity<SessionResponse> signedIn(Tokens tokens) {
        Instant now = clock.instant();
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS_COOKIE, tokens.accessToken(), "/",
                Duration.between(now, tokens.accessExpiresAt())).toString());
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH_COOKIE, tokens.refreshToken(), REFRESH_COOKIE_PATH,
                Duration.between(now, tokens.refreshExpiresAt())).toString());
        return ResponseEntity.ok().headers(headers).body(body(tokens.user(), tokens.accessExpiresAt()));
    }

    private SessionResponse body(User user, Instant accessExpiresAt) {
        Ability ability = Ability.forRole(user.getRole(), user.getCountryScope());
        List<Map<String, Object>> rules = ability.rules().stream().map(Ability.Rule::toCasl).toList();
        UserView view = new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCountryScope());
        return new SessionResponse(view, accessExpiresAt, rules);
    }

    /**
     * HttpOnly = JavaScript cannot read it. SameSite=Strict = the browser only sends it from our own site, which
     * stops other websites from making requests in the user's name. Secure = HTTPS only (off for local http).
     */
    private ResponseCookie cookie(String name, String value, String path, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}
