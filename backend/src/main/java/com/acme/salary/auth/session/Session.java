package com.acme.salary.auth.session;

import com.acme.salary.auth.token.AccessToken;
import com.acme.salary.auth.user.User;
import java.time.Instant;

/** The result of signing in or refreshing: who, plus the freshly issued token pair. */
public record Session(User user, AccessToken access, IssuedRefreshToken refresh) {

    /** The raw refresh token value, returned to the client once and never stored. */
    public record IssuedRefreshToken(String value, Instant expiresAt) {
    }
}
