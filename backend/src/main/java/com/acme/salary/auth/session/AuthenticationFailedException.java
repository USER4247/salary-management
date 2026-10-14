package com.acme.salary.auth.session;

/** Deliberately vague: callers must not learn whether the user, the password or the token was wrong. */
public class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException(String message) {
        super(message);
    }
}
