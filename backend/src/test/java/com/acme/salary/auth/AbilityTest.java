package com.acme.salary.auth;

import static com.acme.salary.auth.Ability.Action.CREATE;
import static com.acme.salary.auth.Ability.Action.DELETE;
import static com.acme.salary.auth.Ability.Action.READ;
import static com.acme.salary.auth.Ability.Action.UPDATE;
import static com.acme.salary.auth.Ability.Subject.EMPLOYEE;
import static com.acme.salary.auth.Ability.Subject.INSIGHTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.acme.salary.auth.Ability.Action;
import com.acme.salary.auth.Ability.Rule;
import com.acme.salary.auth.Ability.Subject;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AbilityTest {

    @Test
    void nothingIsAllowedWithoutARule() {
        Ability ability = new Ability(List.of());

        assertThat(ability.can(READ, EMPLOYEE)).isFalse();
    }

    @Test
    void superadminCanDoAnythingToAnything() {
        Ability ability = Ability.forRole(Role.SUPERADMIN, null);

        for (Action action : Action.values()) {
            for (Subject subject : Subject.values()) {
                assertThat(ability.can(action, subject)).as(action + " " + subject).isTrue();
            }
        }
    }

    @Test
    void hrManagerManagesEmployeesButOnlyReadsInsights() {
        Ability ability = Ability.forRole(Role.HR_MANAGER, null);

        assertThat(ability.can(DELETE, EMPLOYEE)).isTrue();
        assertThat(ability.can(READ, INSIGHTS)).isTrue();
        assertThat(ability.can(UPDATE, INSIGHTS)).isFalse();
    }

    @Test
    void viewerCanOnlyRead() {
        Ability ability = Ability.forRole(Role.HR_VIEWER, null);

        assertThat(ability.can(READ, EMPLOYEE)).isTrue();
        assertThat(ability.can(READ, INSIGHTS)).isTrue();
        assertThat(ability.can(CREATE, EMPLOYEE)).isFalse();
        assertThat(ability.can(DELETE, EMPLOYEE)).isFalse();
    }

    @Test
    void countryHrIsLimitedToTheirCountryAndCannotDelete() {
        Ability ability = Ability.forRole(Role.COUNTRY_HR, "IN");

        assertThat(ability.can(UPDATE, EMPLOYEE, Map.of("country", "IN"))).isTrue();
        assertThat(ability.can(UPDATE, EMPLOYEE, Map.of("country", "US"))).isFalse();
        assertThat(ability.can(READ, INSIGHTS, Map.of("country", "US"))).isFalse();
        assertThat(ability.can(DELETE, EMPLOYEE, Map.of("country", "IN"))).isFalse();
    }

    @Test
    void conditionalRuleCountsForTheTypeCheckButNotForOtherResources() {
        Ability ability = Ability.forRole(Role.COUNTRY_HR, "IN");

        assertThat(ability.can(READ, EMPLOYEE)).isTrue();                               // "may read some employees"
        assertThat(ability.can(READ, EMPLOYEE, Map.of())).isFalse();                    // this item has no country
    }

    @Test
    void countryHrWithoutACountryIsRejected() {
        assertThatThrownBy(() -> Ability.forRole(Role.COUNTRY_HR, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("countryScope");
    }

    @Test
    void rulesUseCaslWireFormatSoTheAngularAppCanLoadThem() {
        assertThat(Rule.of(Action.MANAGE, Subject.ALL).toCasl()).isEqualTo(Map.of("action", "manage", "subject", "all"));
        assertThat(Rule.of(READ, EMPLOYEE, Map.of("country", "IN")).toCasl()).isEqualTo(
                Map.of("action", "read", "subject", "Employee", "conditions", Map.of("country", "IN")));
    }
}
