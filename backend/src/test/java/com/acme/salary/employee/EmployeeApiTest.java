package com.acme.salary.employee;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
 * The employee list through the real app. Six hand-made employees keep the expected answers obvious:
 * 3 in India (IN), 2 in the United States (US) and 1 in Germany (DE).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EmployeeRepository employees;

    @BeforeEach
    void createSixEmployees() {
        employees.deleteAll();
        employees.saveAll(List.of(
                employee("Asha Rao", "asha@acme.test", "IN", "Engineering", "Software Engineer", 2_000_000, "INR", "ACTIVE"),
                employee("Ravi Iyer", "ravi@acme.test", "IN", "Sales", "Account Executive", 1_500_000, "INR", "ACTIVE"),
                employee("Meera Nair", "meera@acme.test", "IN", "Engineering", "Staff Engineer", 4_000_000, "INR", "ON_LEAVE"),
                employee("John Smith", "john@acme.test", "US", "Engineering", "Software Engineer", 120_000, "USD", "ACTIVE"),
                employee("Jane Doe", "jane@acme.test", "US", "Finance", "Accountant", 70_000, "USD", "TERMINATED"),
                employee("Lukas Weber", "lukas@acme.test", "DE", "Operations", "Operations Manager", 95_000, "EUR", "ACTIVE")));
    }

    // ----- who sees what -----

    @Test
    void superadminHrManagerAndViewerSeeEveryone() throws Exception {
        for (String email : List.of("superadmin@acme.test", "manager@acme.test", "viewer@acme.test")) {
            mvc.perform(get("/api/employees").cookie(signedInAs(email)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(6));
        }
    }

    @Test
    void aRegionalOfficerSeesOnlyTheirOwnCountry() throws Exception {
        mvc.perform(get("/api/employees").cookie(signedInAs("hr.in@acme.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.content[*].country").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is("IN"))));

        mvc.perform(get("/api/employees").cookie(signedInAs("hr.de@acme.test")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Lukas Weber"));
    }

    @Test
    void aRegionalOfficerCannotReachOtherCountriesByAskingForThem() throws Exception {
        mvc.perform(get("/api/employees").param("country", "US").cookie(signedInAs("hr.in@acme.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));       // the limit wins over the filter
        mvc.perform(get("/api/employees").param("search", "john").cookie(signedInAs("hr.in@acme.test")))
                .andExpect(jsonPath("$.totalElements").value(0));       // and over the search
    }

    @Test
    void youMustBeSignedIn() throws Exception {
        mvc.perform(get("/api/employees")).andExpect(status().isUnauthorized());
    }

    // ----- searching and filtering -----

    @Test
    void searchMatchesNameEmailAndJobTitleIgnoringCase() throws Exception {
        Cookie manager = signedInAs("manager@acme.test");

        mvc.perform(get("/api/employees").param("search", "MEERA").cookie(manager)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/employees").param("search", "jane@acme").cookie(manager)).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/employees").param("search", "software eng").cookie(manager)).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void searchTreatsPercentAndUnderscoreAsPlainText() throws Exception {
        mvc.perform(get("/api/employees").param("search", "%").cookie(signedInAs("manager@acme.test")))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void filtersCanBeCombined() throws Exception {
        mvc.perform(get("/api/employees").param("country", "IN").param("department", "Engineering").param("status", "ACTIVE")
                        .cookie(signedInAs("manager@acme.test")))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].fullName").value("Asha Rao"));
    }

    @Test
    void anUnknownStatusIsABadRequest() throws Exception {
        mvc.perform(get("/api/employees").param("status", "FIRED").cookie(signedInAs("manager@acme.test")))
                .andExpect(status().isBadRequest());
    }

    // ----- paging and sorting -----

    @Test
    void resultsArePagedWithTotals() throws Exception {
        mvc.perform(get("/api/employees").param("size", "4").param("page", "1").cookie(signedInAs("manager@acme.test")))
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(4))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void pageSizeIsCappedAt100() throws Exception {
        mvc.perform(get("/api/employees").param("size", "100000").cookie(signedInAs("manager@acme.test")))
                .andExpect(jsonPath("$.size").value(100));
    }

    @Test
    void canSortBySalaryHighestFirst() throws Exception {
        mvc.perform(get("/api/employees").param("sort", "annualSalary,desc").param("country", "IN").cookie(signedInAs("manager@acme.test")))
                .andExpect(jsonPath("$.content[0].fullName").value("Meera Nair"))
                .andExpect(jsonPath("$.content[2].fullName").value("Ravi Iyer"));
    }

    @Test
    void sortsByNameWhenNothingElseIsAsked() throws Exception {
        mvc.perform(get("/api/employees").cookie(signedInAs("manager@acme.test")))
                .andExpect(jsonPath("$.content[0].fullName").value("Asha Rao"))
                .andExpect(jsonPath("$.content[5].fullName").value("Ravi Iyer"));
    }

    @Test
    void sortingByAnUnknownColumnIsRejected() throws Exception {
        mvc.perform(get("/api/employees").param("sort", "password_hash,asc").cookie(signedInAs("manager@acme.test")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eachEmployeeCarriesTheFieldsTheScreenNeeds() throws Exception {
        mvc.perform(get("/api/employees").param("search", "lukas").cookie(signedInAs("superadmin@acme.test")))
                .andExpect(jsonPath("$.content[0].email").value("lukas@acme.test"))
                .andExpect(jsonPath("$.content[0].department").value("Operations"))
                .andExpect(jsonPath("$.content[0].jobTitle").value("Operations Manager"))
                .andExpect(jsonPath("$.content[0].annualSalary").value(95_000))
                .andExpect(jsonPath("$.content[0].currency").value("EUR"))
                .andExpect(jsonPath("$.content[0].status").value("ACTIVE"));
    }

    // ----- helpers -----

    private Cookie signedInAs(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"Demo@12345\"}";
        return mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("access_token");
    }

    private static Employee employee(String name, String email, String country, String department, String title,
                                     long salary, String currency, String status) {
        return new Employee(name, email, country, department, title, salary, currency, "2020-01-15", status);
    }
}
