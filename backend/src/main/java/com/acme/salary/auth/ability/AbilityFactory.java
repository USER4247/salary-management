package com.acme.salary.auth.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/** Builds an {@link Ability} from the identity claims in a token. The single place permissions are defined. */
@Component
public class AbilityFactory {

    public Ability forRole(Role role, String countryScope) {
        List<Rule> rules = new ArrayList<>();
        switch (role) {
            case HR_ADMIN -> {
                for (Action action : Action.values()) {
                    rules.add(Rule.of(action, Subject.EMPLOYEE));
                }
                rules.add(Rule.of(Action.READ, Subject.INSIGHTS));
            }
            case HR_VIEWER -> {
                rules.add(Rule.of(Action.READ, Subject.EMPLOYEE));
                rules.add(Rule.of(Action.READ, Subject.INSIGHTS));
            }
            case COUNTRY_HR -> {
                if (countryScope == null || countryScope.isBlank()) {
                    throw new IllegalArgumentException("COUNTRY_HR requires a countryScope");
                }
                Map<String, Object> inCountry = Map.of("country", countryScope);
                rules.add(Rule.of(Action.READ, Subject.EMPLOYEE, inCountry));
                rules.add(Rule.of(Action.CREATE, Subject.EMPLOYEE, inCountry));
                rules.add(Rule.of(Action.UPDATE, Subject.EMPLOYEE, inCountry));
                rules.add(Rule.of(Action.READ, Subject.INSIGHTS, inCountry));
            }
        }
        return new Ability(rules);
    }
}
