package com.acme.salary.insights;

import com.acme.salary.auth.Ability.Action;
import com.acme.salary.auth.Ability.Subject;
import com.acme.salary.auth.AuthContext;
import com.acme.salary.auth.CheckAbility;
import com.acme.salary.insights.InsightsService.GroupBy;
import com.acme.salary.insights.InsightsService.PayInsights;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/insights")
class InsightsController {

    private static final Set<String> STATUSES = Set.of("ACTIVE", "ON_LEAVE", "TERMINATED", "ALL");

    private final InsightsService insights;
    private final AuthContext authContext;

    InsightsController(InsightsService insights, AuthContext authContext) {
        this.insights = insights;
        this.authContext = authContext;
    }

    /** Example: GET /api/insights/pay?groupBy=department&status=ACTIVE. By default only active employees count. */
    @GetMapping("/pay")
    @CheckAbility(action = Action.READ, subject = Subject.INSIGHTS)   // policy guard: must be allowed to read insights
    PayInsights pay(@RequestParam(defaultValue = "country") String groupBy,
                    @RequestParam(defaultValue = "ACTIVE") String status) {
        if (!STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be one of " + STATUSES);
        }
        return insights.pay(parseGroupBy(groupBy), status, authContext.currentAbility());
    }

    /** Accepts "country", "department" and "job-title" (or "job_title"). */
    private static GroupBy parseGroupBy(String text) {
        try {
            return GroupBy.valueOf(text.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "groupBy must be country, department or job-title");
        }
    }
}
