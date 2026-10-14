package com.acme.salary.auth;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * "Who is calling right now?" Inject this into a controller to read the signed-in user's token and abilities.
 * Example: {@code authContext.currentAbility().can(UPDATE, EMPLOYEE, Map.of("country", employee.getCountry()))}.
 */
@Component
public class AuthContext {

    public Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AccessDeniedException("Nobody is signed in");
    }

    public Role currentRole() {
        return Role.valueOf(currentJwt().getClaimAsString("role"));
    }

    /** Rebuilt from the role in the token on every call, so permission changes never go stale. */
    public Ability currentAbility() {
        Jwt jwt = currentJwt();
        return Ability.forRole(Role.valueOf(jwt.getClaimAsString("role")), jwt.getClaimAsString("countryScope"));
    }
}
