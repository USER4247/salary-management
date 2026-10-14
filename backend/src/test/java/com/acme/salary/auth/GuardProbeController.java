package com.acme.salary.auth;

import com.acme.salary.auth.Ability.Action;
import com.acme.salary.auth.Ability.Subject;
import java.util.Map;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Test-only endpoints, one per guard, so the guards can be tested before real endpoints exist. */
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

    @GetMapping("/managers-only")
    @RequireRole(Role.HR_MANAGER)
    String managersOnly() {
        return "managers";
    }

    /** Two steps: the guard checks "may update employees at all", then the code checks this one country. */
    @PutMapping("/employees/in/{country}")
    @CheckAbility(action = Action.UPDATE, subject = Subject.EMPLOYEE)
    String updateInCountry(@PathVariable String country) {
        if (!authContext.currentAbility().can(Action.UPDATE, Subject.EMPLOYEE, Map.of("country", country))) {
            throw new AccessDeniedException("Not allowed to update employees in " + country);
        }
        return "updated";
    }
}
