package com.acme.salary.auth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * "What is this person allowed to do?", modelled like CASL (the same idea is used in the Angular app).
 *
 * <p>An ability is a list of rules. Each rule says "this ACTION is allowed on this SUBJECT", and may add a
 * condition such as "only where country = IN". Nothing is allowed unless a rule says so (deny by default).
 */
public final class Ability {

    /** MANAGE is a wildcard meaning "any action" (same word CASL uses). */
    public enum Action {
        MANAGE, READ, CREATE, UPDATE, DELETE;

        /** Lower-case name, which is what the Angular app expects. */
        public String wire() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** ALL is a wildcard meaning "any subject" (same word CASL uses). */
    public enum Subject {
        ALL("all"), EMPLOYEE("Employee"), INSIGHTS("Insights");

        private final String wire;

        Subject(String wire) {
            this.wire = wire;
        }

        public String wire() {
            return wire;
        }
    }

    /** One permission. {@code conditions} is empty for "no extra limits", or e.g. {country: "IN"}. */
    public record Rule(Action action, Subject subject, Map<String, Object> conditions) {

        static Rule of(Action action, Subject subject) {
            return new Rule(action, subject, Map.of());
        }

        static Rule of(Action action, Subject subject, Map<String, Object> conditions) {
            return new Rule(action, subject, conditions);
        }

        /** Does this rule cover the requested action and subject? (Wildcards cover everything.) */
        boolean covers(Action wantedAction, Subject wantedSubject) {
            boolean actionOk = action == Action.MANAGE || action == wantedAction;
            boolean subjectOk = subject == Subject.ALL || subject == wantedSubject;
            return actionOk && subjectOk;
        }

        /** Do the conditions hold for this specific resource, e.g. an employee in country IN? */
        boolean conditionsHold(Map<String, ?> resource) {
            return conditions.entrySet().stream()
                    .allMatch(c -> c.getValue().equals(resource.get(c.getKey())));
        }

        /** The rule as the JSON shape CASL understands, so the Angular app can load it directly. */
        public Map<String, Object> toCasl() {
            Map<String, Object> json = new HashMap<>();
            json.put("action", action.wire());
            json.put("subject", subject.wire());
            if (!conditions.isEmpty()) {
                json.put("conditions", conditions);
            }
            return json;
        }
    }

    private final List<Rule> rules;

    public Ability(List<Rule> rules) {
        this.rules = List.copyOf(rules);
    }

    /** The single place where each role's permissions are defined. */
    public static Ability forRole(Role role, String countryScope) {
        List<Rule> rules = new ArrayList<>();
        switch (role) {
            case SUPERADMIN -> rules.add(Rule.of(Action.MANAGE, Subject.ALL));
            case HR_MANAGER -> {
                rules.add(Rule.of(Action.MANAGE, Subject.EMPLOYEE));
                rules.add(Rule.of(Action.READ, Subject.INSIGHTS));
            }
            case HR_VIEWER -> {
                rules.add(Rule.of(Action.READ, Subject.EMPLOYEE));
                rules.add(Rule.of(Action.READ, Subject.INSIGHTS));
            }
            case COUNTRY_HR -> {
                if (countryScope == null || countryScope.isBlank()) {
                    throw new IllegalArgumentException("COUNTRY_HR needs a countryScope");
                }
                Map<String, Object> onlyMyCountry = Map.of("country", countryScope);
                rules.add(Rule.of(Action.READ, Subject.EMPLOYEE, onlyMyCountry));
                rules.add(Rule.of(Action.CREATE, Subject.EMPLOYEE, onlyMyCountry));
                rules.add(Rule.of(Action.UPDATE, Subject.EMPLOYEE, onlyMyCountry));
                rules.add(Rule.of(Action.READ, Subject.INSIGHTS, onlyMyCountry));
            }
        }
        return new Ability(rules);
    }

    /** "Can I do this kind of thing at all?" A conditional rule counts: the check on the exact item comes later. */
    public boolean can(Action action, Subject subject) {
        return rules.stream().anyMatch(rule -> rule.covers(action, subject));
    }

    /** "Can I do this to this specific item?" e.g. update the employee whose country is US. */
    public boolean can(Action action, Subject subject, Map<String, ?> resource) {
        return rules.stream().anyMatch(rule -> rule.covers(action, subject) && rule.conditionsHold(resource));
    }

    /**
     * "Which countries is this person limited to for this action?" Empty means no limit (any country).
     * This turns the ability into something a database query can use, so data is filtered at the source.
     * Only the "country" condition is understood, which is the one COUNTRY_HR uses.
     */
    public Optional<Set<String>> countryLimit(Action action, Subject subject) {
        List<Rule> matching = rules.stream().filter(rule -> rule.covers(action, subject)).toList();
        if (matching.stream().anyMatch(rule -> rule.conditions().isEmpty())) {
            return Optional.empty();   // one unconditional rule is enough: no limit
        }
        Set<String> countries = matching.stream()
                .map(rule -> rule.conditions().get("country"))
                .filter(Objects::nonNull)
                .map(Object::toString)
                .collect(Collectors.toSet());
        return Optional.of(countries);   // may be empty: then nothing at all is allowed
    }

    public List<Rule> rules() {
        return rules;
    }
}
