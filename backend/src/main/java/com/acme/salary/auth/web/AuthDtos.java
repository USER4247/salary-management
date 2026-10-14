package com.acme.salary.auth.web;

import com.acme.salary.auth.ability.Role;
import com.acme.salary.auth.user.User;
import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Request and response shapes of the auth API. */
final class AuthDtos {

    private AuthDtos() {
    }

    record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    record UserView(Long id, String email, String fullName, Role role, String countryScope) {
        static UserView of(User user) {
            return new UserView(user.getId(), user.getEmail(), user.getFullName(), user.getRole(), user.getCountryScope());
        }
    }

    /**
     * Returned by login, refresh and /me. The tokens themselves travel only in HttpOnly cookies; the
     * client learns when the access token expires and which abilities to show (CASL raw rules).
     */
    record SessionResponse(UserView user, Instant accessExpiresAt, List<Map<String, Object>> rules) {
    }
}
