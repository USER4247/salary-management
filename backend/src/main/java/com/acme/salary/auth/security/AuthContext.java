package com.acme.salary.auth.security;

import com.acme.salary.auth.ability.Ability;
import com.acme.salary.auth.ability.AbilityFactory;
import com.acme.salary.auth.ability.Role;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * The authenticated caller as seen by application code. Inject it into a handler to make
 * resource-level checks, for example {@code authContext.currentAbility().can(UPDATE, EMPLOYEE, Map.of("country", c))}.
 */
@Component
public class AuthContext {

    private final AbilityFactory abilityFactory;

    public AuthContext(AbilityFactory abilityFactory) {
        this.abilityFactory = abilityFactory;
    }

    public Jwt currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt;
        }
        throw new AccessDeniedException("No authenticated user");
    }

    public Role currentRole() {
        return Role.valueOf(currentJwt().getClaimAsString("role"));
    }

    public Ability currentAbility() {
        Jwt jwt = currentJwt();
        return abilityFactory.forRole(Role.valueOf(jwt.getClaimAsString("role")), jwt.getClaimAsString("countryScope"));
    }
}
