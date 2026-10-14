package com.acme.salary.auth.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AbilityTest {

    @Test
    void noRulesMeansNothingIsAllowed() {
        Ability ability = new Ability(List.of());

        assertThat(ability.can(Action.READ, Subject.EMPLOYEE)).isFalse();
    }

    @Test
    void unconditionalRuleAllowsAnyResource() {
        Ability ability = new Ability(List.of(Rule.of(Action.READ, Subject.EMPLOYEE)));

        assertThat(ability.can(Action.READ, Subject.EMPLOYEE, Map.of("country", "US"))).isTrue();
    }

    @Test
    void conditionalRuleAllowsTheTypeButOnlyMatchingResources() {
        Ability ability = new Ability(List.of(Rule.of(Action.READ, Subject.EMPLOYEE, Map.of("country", "IN"))));

        assertThat(ability.can(Action.READ, Subject.EMPLOYEE)).isTrue();
        assertThat(ability.can(Action.READ, Subject.EMPLOYEE, Map.of("country", "IN"))).isTrue();
        assertThat(ability.can(Action.READ, Subject.EMPLOYEE, Map.of("country", "US"))).isFalse();
        assertThat(ability.can(Action.READ, Subject.EMPLOYEE, Map.of())).isFalse();
    }

    @Test
    void rulesForOtherActionsOrSubjectsDoNotApply() {
        Ability ability = new Ability(List.of(Rule.of(Action.READ, Subject.INSIGHTS)));

        assertThat(ability.can(Action.READ, Subject.EMPLOYEE)).isFalse();
        assertThat(ability.can(Action.UPDATE, Subject.INSIGHTS)).isFalse();
    }

    @Test
    void ruleSerializesToCaslRawFormat() {
        Rule rule = Rule.of(Action.READ, Subject.EMPLOYEE, Map.of("country", "IN"));

        assertThat(rule.toCaslRule()).isEqualTo(
                Map.of("action", "read", "subject", "Employee", "conditions", Map.of("country", "IN")));
        assertThat(Rule.of(Action.READ, Subject.INSIGHTS).toCaslRule())
                .isEqualTo(Map.of("action", "read", "subject", "Insights"));
    }
}
