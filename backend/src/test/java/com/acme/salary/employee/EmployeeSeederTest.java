package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** The first-start loader used on a fresh deployment, checked against the real bundled data file. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.seed.employees=true")
class EmployeeSeederTest {

    @Autowired
    private EmployeeSeeder seeder;
    @Autowired
    private EmployeeRepository employees;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void startAndEndEmpty() {
        employees.deleteAll();
    }

    @Test
    void loadsTenThousandEmployeesIntoAnEmptyDatabase() {
        seeder.run(null);

        assertThat(employees.count()).isEqualTo(10_000);
        // every currency used has an exchange rate, so no one is left out of the insights
        Integer withoutRate = jdbc.queryForObject(
                "SELECT COUNT(*) FROM employees WHERE currency NOT IN ('USD','INR','GBP','EUR','CAD','AUD','JPY','SGD','BRL')", Integer.class);
        assertThat(withoutRate).isZero();
    }

    @Test
    void coversAllTenCountriesWithTheExpectedMix() {
        seeder.run(null);

        Map<String, Object> countries = jdbc.queryForMap("SELECT COUNT(DISTINCT country) AS n FROM employees");
        assertThat(countries.get("n")).isEqualTo(10);
        Integer active = jdbc.queryForObject("SELECT COUNT(*) FROM employees WHERE status = 'ACTIVE'", Integer.class);
        assertThat(active).isBetween(9_000, 9_500);   // about 92% are active
    }

    @Test
    void doesNothingWhenEmployeesAlreadyExist() {
        seeder.run(null);

        seeder.run(null);   // a restart of the app: must not duplicate or fail on the unique emails

        assertThat(employees.count()).isEqualTo(10_000);
    }
}
