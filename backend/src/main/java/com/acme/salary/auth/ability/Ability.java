package com.acme.salary.auth.ability;

import java.util.List;
import java.util.Map;

/** What a principal may do. Deny by default: only an explicit rule grants access. */
public final class Ability {

    private final List<Rule> rules;

    public Ability(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** Type-level check: is there any rule for this action on this subject (conditional or not)? */
    public boolean can(Action action, Subject subject) {
        return rules.stream().anyMatch(rule -> rule.appliesTo(action, subject));
    }

    /** Instance-level check: does a rule allow this action on this specific resource? */
    public boolean can(Action action, Subject subject, Map<String, ?> resource) {
        return rules.stream()
                .filter(rule -> rule.appliesTo(action, subject))
                .anyMatch(rule -> rule.matches(resource));
    }

    public List<Rule> rules() {
        return rules;
    }
}
