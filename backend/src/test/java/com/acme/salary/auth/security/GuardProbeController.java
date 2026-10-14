package com.acme.salary.auth.security;

import com.acme.salary.auth.ability.Action;
import com.acme.salary.auth.ability.Role;
import com.acme.salary.auth.ability.Subject;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints that exercise each guard; they are not part of the product API. */
@RestController
@RequestMapping("/api/probe")
class GuardProbeController {

    private final AuthContext authContext;

    GuardProbeController(AuthContext authContext) {
        this.authContext = authContext;
    }

    @GetMapping("/employees")
    @CheckAbility(action = Action.READ, subject = Subject.EMPLOYEE)
    String read() {
        return "read";
    }

    @PostMapping("/employees")
    @CheckAbility(action = Action.CREATE, subject = Subject.EMPLOYEE)
    String create() {
        return "create";
    }

    @DeleteMapping("/employees/1")
    @CheckAbility(action = Action.DELETE, subject = Subject.EMPLOYEE)
    String delete() {
        return "delete";
    }

    @GetMapping("/admin-only")
    @RequireRole(Role.HR_ADMIN)
    String adminOnly() {
        return "admin";
    }

    /** Resource-level check: the type-level guard passes, then the concrete resource is checked. */
    @PutMapping("/employees/in/{country}")
    @CheckAbility(action = Action.UPDATE, subject = Subject.EMPLOYEE)
    String updateInCountry(@PathVariable String country) {
        if (!authContext.currentAbility().can(Action.UPDATE, Subject.EMPLOYEE, Map.of("country", country))) {
            throw new AccessDeniedException("Not allowed to update employees in " + country);
        }
        return "updated";
    }
}
