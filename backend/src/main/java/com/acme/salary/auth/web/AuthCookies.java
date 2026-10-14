package com.acme.salary.auth.web;

import com.acme.salary.auth.AuthProperties;
import com.acme.salary.auth.session.Session;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Builds the two auth cookies. Both are HttpOnly and SameSite=Strict; the refresh token is additionally
 * scoped to /api/auth so it is only ever sent to the refresh and logout endpoints.
 */
@Component
public class AuthCookies {

    public static final String ACCESS = "access_token";
    public static final String REFRESH = "refresh_token";
    static final String REFRESH_PATH = "/api/auth";

    private final AuthProperties properties;
    private final Clock clock;

    public AuthCookies(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** Both Set-Cookie headers for a freshly issued session. */
    public HttpHeaders issue(Session session) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie(ACCESS, session.access().value(), "/", session.access().expiresAt()).toString());
        headers.add(HttpHeaders.SET_COOKIE, cookie(REFRESH, session.refresh().value(), REFRESH_PATH, session.refresh().expiresAt()).toString());
        return headers;
    }

    /** Expires both cookies in the browser. */
    public HttpHeaders clear() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, expired(ACCESS, "/").toString());
        headers.add(HttpHeaders.SET_COOKIE, expired(REFRESH, REFRESH_PATH).toString());
        return headers;
    }

    private ResponseCookie cookie(String name, String value, String path, Instant expiresAt) {
        return base(name, value, path).maxAge(Duration.between(clock.instant(), expiresAt)).build();
    }

    private ResponseCookie expired(String name, String path) {
        return base(name, "", path).maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String name, String value, String path) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(properties.cookieSecure())
                .sameSite("Strict")
                .path(path);
    }
}
