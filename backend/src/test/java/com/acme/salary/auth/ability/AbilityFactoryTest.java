package com.acme.salary.auth.ability;

import static com.acme.salary.auth.ability.Action.CREATE;
import static com.acme.salary.auth.ability.Action.DELETE;
import static com.acme.salary.auth.ability.Action.READ;
import static com.acme.salary.auth.ability.Action.UPDATE;
import static com.acme.salary.auth.ability.Subject.EMPLOYEE;
import static com.acme.salary.auth.ability.Subject.INSIGHTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AbilityFactoryTest {

    private final AbilityFactory factory = new AbilityFactory();

    @Test
    void adminCanDoEverythingOnEmployeesAndReadInsights() {
        Ability ability = factory.forRole(Role.HR_ADMIN, null);

        for (Action action : Action.values()) {
            assertThat(ability.can(action, EMPLOYEE)).as(action.name()).isTrue();
        }
        assertThat(ability.can(READ, INSIGHTS)).isTrue();
    }

    @Test
    void viewerCanOnlyRead() {
        Ability ability = factory.forRole(Role.HR_VIEWER, null);

        assertThat(ability.can(READ, EMPLOYEE)).isTrue();
        assertThat(ability.can(READ, INSIGHTS)).isTrue();
        assertThat(ability.can(CREATE, EMPLOYEE)).isFalse();
        assertThat(ability.can(UPDATE, EMPLOYEE)).isFalse();
        assertThat(ability.can(DELETE, EMPLOYEE)).isFalse();
    }

    @Test
    void countryHrIsLimitedToTheirCountry() {
        Ability ability = factory.forRole(Role.COUNTRY_HR, "IN");

        assertThat(ability.can(UPDATE, EMPLOYEE, Map.of("country", "IN"))).isTrue();
        assertThat(ability.can(UPDATE, EMPLOYEE, Map.of("country", "US"))).isFalse();
        assertThat(ability.can(READ, INSIGHTS, Map.of("country", "US"))).isFalse();
    }

    @Test
    void countryHrCannotDeleteEvenInTheirCountry() {
        Ability ability = factory.forRole(Role.COUNTRY_HR, "IN");

        assertThat(ability.can(DELETE, EMPLOYEE)).isFalse();
        assertThat(ability.can(DELETE, EMPLOYEE, Map.of("country", "IN"))).isFalse();
    }

    @Test
    void countryHrWithoutScopeIsRejected() {
        assertThatThrownBy(() -> factory.forRole(Role.COUNTRY_HR, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("countryScope");
    }
}
