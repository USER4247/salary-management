package com.acme.salary.auth.ability;

import java.util.HashMap;
import java.util.Map;

/** One permission: an action on a subject, optionally limited by equality conditions on the resource. */
public record Rule(Action action, Subject subject, Map<String, Object> conditions) {

    public Rule {
        conditions = conditions == null ? Map.of() : Map.copyOf(conditions);
    }

    public static Rule of(Action action, Subject subject) {
        return new Rule(action, subject, Map.of());
    }

    public static Rule of(Action action, Subject subject, Map<String, Object> conditions) {
        return new Rule(action, subject, conditions);
    }

    boolean appliesTo(Action requested, Subject requestedSubject) {
        return action == requested && subject == requestedSubject;
    }

    boolean matches(Map<String, ?> resource) {
        return conditions.entrySet().stream()
                .allMatch(condition -> condition.getValue().equals(resource.get(condition.getKey())));
    }

    /** The rule in CASL's raw JSON shape, so the UI can load it into its own ability. */
    public Map<String, Object> toCaslRule() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("action", action.wire());
        raw.put("subject", subject.wire());
        if (!conditions.isEmpty()) {
            raw.put("conditions", conditions);
        }
        return raw;
    }
}
