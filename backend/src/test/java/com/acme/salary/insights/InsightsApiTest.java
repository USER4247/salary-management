package com.acme.salary.insights;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.salary.employee.Employee;
import com.acme.salary.employee.EmployeeRepository;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pay insights through the real app. The data is chosen so every answer can be checked by hand.
 * All salaries equal a round number of US dollars at the fixed rates (83 INR = 1 USD, 0.92 EUR = 1 USD):
 *
 * <pre>
 *   US  Engineering  Software Engineer  $100,000      IN  Engineering  Software Engineer  INR  8,300,000 = $100,000
 *   US  Engineering  Software Engineer  $200,000      IN  Sales        Sales Manager      INR 16,600,000 = $200,000
 *   US  Sales        Sales Manager      $300,000      DE  Engineering  Software Engineer  EUR     92,000 = $100,000
 *   US  Engineering  Software Engineer  $900,000  (TERMINATED, so left out unless status=ALL)
 * </pre>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InsightsApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EmployeeRepository employees;

    @BeforeEach
    void createTheDataset() {
        employees.deleteAll();
        employees.saveAll(List.of(
                person("US", "Engineering", "Software Engineer", 100_000, "USD", "ACTIVE"),
                person("US", "Engineering", "Software Engineer", 200_000, "USD", "ACTIVE"),
                person("US", "Sales", "Sales Manager", 300_000, "USD", "ACTIVE"),
                person("IN", "Engineering", "Software Engineer", 8_300_000, "INR", "ACTIVE"),
                person("IN", "Sales", "Sales Manager", 16_600_000, "INR", "ACTIVE"),
                person("DE", "Engineering", "Software Engineer", 92_000, "EUR", "ACTIVE"),
                person("US", "Engineering", "Software Engineer", 900_000, "USD", "TERMINATED")));
    }

    @Test
    void overallFiguresCountOnlyActiveEmployeesInDollars() throws Exception {
        // active salaries in dollars, sorted: 100k 100k 100k 200k 200k 300k
        mvc.perform(get("/api/insights/pay").cookie(signedInAs("manager@acme.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.overall.headcount").value(6))
                .andExpect(jsonPath("$.overall.total").value(1_000_000))
                .andExpect(jsonPath("$.overall.average").value(166_667))
                .andExpect(jsonPath("$.overall.median").value(150_000))   // an even count: average of 100k and 200k
                .andExpect(jsonPath("$.overall.min").value(100_000))
                .andExpect(jsonPath("$.overall.max").value(300_000));
    }

    @Test
    void groupsByCountryWithMedianForOddAndEvenSizedGroups() throws Exception {
        mvc.perform(get("/api/insights/pay").param("groupBy", "country").cookie(signedInAs("manager@acme.test")))
                .andExpect(jsonPath("$.groups.length()").value(3))
                // groups are sorted by name: DE, IN, US
                .andExpect(jsonPath("$.groups[0].key").value("DE"))
                .andExpect(jsonPath("$.groups[0].headcount").value(1))
                .andExpect(jsonPath("$.groups[0].median").value(100_000))
                .andExpect(jsonPath("$.groups[1].key").value("IN"))
                .andExpect(jsonPath("$.groups[1].median").value(150_000))      // two people: 100k and 200k
                .andExpect(jsonPath("$.groups[1].total").value(300_000))
                .andExpect(jsonPath("$.groups[2].key").value("US"))
                .andExpect(jsonPath("$.groups[2].median").value(200_000))      // three people: the middle one
                .andExpect(jsonPath("$.groups[2].average").value(200_000))
                .andExpect(jsonPath("$.groups[2].min").value(100_000))
                .andExpect(jsonPath("$.groups[2].max").value(300_000));
    }

    @Test
    void groupsByDepartmentAndByJobTitle() throws Exception {
        Cookie manager = signedInAs("manager@acme.test");

        mvc.perform(get("/api/insights/pay").param("groupBy", "department").cookie(manager))
                .andExpect(jsonPath("$.groups[0].key").value("Engineering"))
                .andExpect(jsonPath("$.groups[0].headcount").value(4))
                .andExpect(jsonPath("$.groups[0].total").value(500_000))
                .andExpect(jsonPath("$.groups[0].median").value(100_000))      // 100k 100k 100k 200k: middle two are 100k
                .andExpect(jsonPath("$.groups[1].key").value("Sales"))
                .andExpect(jsonPath("$.groups[1].median").value(250_000));

        mvc.perform(get("/api/insights/pay").param("groupBy", "job-title").cookie(manager))
                .andExpect(jsonPath("$.groups[0].key").value("Sales Manager"))
                .andExpect(jsonPath("$.groups[1].key").value("Software Engineer"));
    }

    @Test
    void statusAllIncludesTerminatedEmployees() throws Exception {
        mvc.perform(get("/api/insights/pay").param("status", "ALL").cookie(signedInAs("manager@acme.test")))
                .andExpect(jsonPath("$.overall.headcount").value(7))
                .andExpect(jsonPath("$.overall.max").value(900_000));
    }

    @Test
    void theResponseStatesTheExchangeRateAssumption() throws Exception {
        mvc.perform(get("/api/insights/pay").cookie(signedInAs("manager@acme.test")))
                .andExpect(jsonPath("$.assumption").value(org.hamcrest.Matchers.containsString("fixed rates")));
    }

    // ----- who sees what -----

    @Test
    void aRegionalOfficerGetsFiguresForTheirOwnCountryOnly() throws Exception {
        mvc.perform(get("/api/insights/pay").cookie(signedInAs("hr.in@acme.test")))
                .andExpect(jsonPath("$.overall.headcount").value(2))
                .andExpect(jsonPath("$.overall.total").value(300_000))
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].key").value("IN"));
    }

    @Test
    void aRegionalOfficerWithNoEmployeesGetsZeroesNotAnError() throws Exception {
        mvc.perform(get("/api/insights/pay").cookie(signedInAs("hr.jp@acme.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overall.headcount").value(0))
                .andExpect(jsonPath("$.groups.length()").value(0));
    }

    @Test
    void viewersAndSuperadminsSeeEveryCountry() throws Exception {
        for (String email : List.of("viewer@acme.test", "superadmin@acme.test")) {
            mvc.perform(get("/api/insights/pay").cookie(signedInAs(email)))
                    .andExpect(jsonPath("$.overall.headcount").value(6));
        }
    }

    @Test
    void youMustBeSignedIn() throws Exception {
        mvc.perform(get("/api/insights/pay")).andExpect(status().isUnauthorized());
    }

    // ----- bad requests -----

    @Test
    void anUnknownGroupingOrStatusIsABadRequest() throws Exception {
        Cookie manager = signedInAs("manager@acme.test");

        mvc.perform(get("/api/insights/pay").param("groupBy", "salary; DROP TABLE employees").cookie(manager))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/insights/pay").param("status", "FIRED").cookie(manager))
                .andExpect(status().isBadRequest());
    }

    // ----- helpers -----

    private Cookie signedInAs(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"Demo@12345\"}";
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("access_token");
    }

    private static Employee person(String country, String department, String title, long salary, String currency, String status) {
        return new Employee("Test Person", "p" + System.nanoTime() + "@acme.test", country, department, title, salary, currency,
                "2020-01-15", status);
    }
}
