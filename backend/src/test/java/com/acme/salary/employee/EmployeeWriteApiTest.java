package com.acme.salary.employee;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** Creating, editing and deleting employees through the real app, and who is allowed to. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EmployeeWriteApiTest {

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EmployeeRepository employees;

    private long indiaId;
    private long usId;

    @BeforeEach
    void twoExistingEmployees() {
        employees.deleteAll();
        indiaId = employees.save(new Employee("Asha Rao", "asha@acme.test", "IN", "Engineering", "Software Engineer",
                2_000_000, "INR", "2020-01-15", "ACTIVE")).getId();
        usId = employees.save(new Employee("John Smith", "john@acme.test", "US", "Sales", "Account Executive",
                120_000, "USD", "2019-05-01", "ACTIVE")).getId();
    }

    private static String json(String email, String country, String currency, String salary) {
        return """
                {"fullName":"New Person","email":"%s","country":"%s","department":"Finance","jobTitle":"Accountant",
                 "annualSalary":%s,"currency":"%s","hireDate":"2024-03-01","status":"ACTIVE"}""".formatted(email, country, salary, currency);
    }

    // ----- create -----

    @Test
    void aManagerCanCreateAnEmployeeAndGetItBack() throws Exception {
        mvc.perform(post("/api/employees").cookie(signedInAs("manager@acme.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("new@acme.test", "US", "USD", "90000")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/employees/")))
                .andExpect(jsonPath("$.fullName").value("New Person"))
                .andExpect(jsonPath("$.hireDate").value("2024-03-01"));

        assertThat(employees.existsByEmailIgnoreCase("new@acme.test")).isTrue();
    }

    @Test
    void aRegionalOfficerCanOnlyCreateInTheirOwnCountry() throws Exception {
        Cookie indiaHr = signedInAs("hr.in@acme.test");

        mvc.perform(post("/api/employees").cookie(indiaHr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("in1@acme.test", "IN", "INR", "1500000")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/employees").cookie(indiaHr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("us1@acme.test", "US", "USD", "90000")))
                .andExpect(status().isForbidden());
    }

    @Test
    void aViewerCannotCreate() throws Exception {
        mvc.perform(post("/api/employees").cookie(signedInAs("viewer@acme.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("v@acme.test", "US", "USD", "90000")))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidInputGetsAMessagePerField() throws Exception {
        String bad = """
                {"fullName":"","email":"not-an-email","country":"india","department":"Finance","jobTitle":"Accountant",
                 "annualSalary":-5,"currency":"INR","hireDate":"2999-01-01","status":"ACTIVE"}""";

        mvc.perform(post("/api/employees").cookie(signedInAs("manager@acme.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(bad))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.country").exists())
                .andExpect(jsonPath("$.errors.annualSalary").value("must be more than 0"))
                .andExpect(jsonPath("$.errors.hireDate").value("cannot be in the future"));
    }

    @Test
    void aCurrencyWithoutAnExchangeRateIsRefused() throws Exception {
        mvc.perform(post("/api/employees").cookie(signedInAs("manager@acme.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("x@acme.test", "US", "XYZ", "90000")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currency").value("Unsupported currency XYZ"));
    }

    @Test
    void anEmailAlreadyInUseIsAConflict() throws Exception {
        mvc.perform(post("/api/employees").cookie(signedInAs("manager@acme.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("ASHA@acme.test", "IN", "INR", "1500000")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errors.email").value("Another employee already uses this email"));
    }

    @Test
    void writesNeedTheCsrfToken() throws Exception {
        mvc.perform(post("/api/employees").cookie(signedInAs("manager@acme.test"))
                        .contentType(MediaType.APPLICATION_JSON).content(json("c@acme.test", "US", "USD", "90000")))
                .andExpect(status().isForbidden());
    }

    @Test
    void youMustBeSignedInToWrite() throws Exception {
        mvc.perform(post("/api/employees").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("a@acme.test", "US", "USD", "90000")))
                .andExpect(status().isUnauthorized());
    }

    // ----- read one -----

    @Test
    void anEmployeeCanBeReadByIdWithinYourRegion() throws Exception {
        mvc.perform(get("/api/employees/" + indiaId).cookie(signedInAs("hr.in@acme.test")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("asha@acme.test"));
    }

    @Test
    void anEmployeeOutsideYourRegionLooksLikeItDoesNotExist() throws Exception {
        mvc.perform(get("/api/employees/" + usId).cookie(signedInAs("hr.in@acme.test"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/employees/999999").cookie(signedInAs("hr.in@acme.test"))).andExpect(status().isNotFound());
    }

    // ----- edit -----

    @Test
    void aManagerCanEditAnEmployee() throws Exception {
        mvc.perform(put("/api/employees/" + usId).cookie(signedInAs("manager@acme.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("john@acme.test", "US", "USD", "135000")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualSalary").value(135_000));

        assertThat(employees.findById(usId).orElseThrow().getAnnualSalary()).isEqualTo(135_000);
    }

    @Test
    void anEmployeeMayKeepTheirOwnEmailWhenEdited() throws Exception {
        mvc.perform(put("/api/employees/" + indiaId).cookie(signedInAs("hr.in@acme.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("asha@acme.test", "IN", "INR", "2200000")))
                .andExpect(status().isOk());
    }

    @Test
    void editingCannotTakeAnotherEmployeesEmail() throws Exception {
        mvc.perform(put("/api/employees/" + usId).cookie(signedInAs("manager@acme.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("asha@acme.test", "US", "USD", "120000")))
                .andExpect(status().isConflict());
    }

    @Test
    void aRegionalOfficerCannotEditOrMoveEmployeesOutsideTheirRegion() throws Exception {
        Cookie indiaHr = signedInAs("hr.in@acme.test");

        // someone in the US: looks like it does not exist
        mvc.perform(put("/api/employees/" + usId).cookie(indiaHr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("john@acme.test", "US", "USD", "130000")))
                .andExpect(status().isNotFound());
        // someone in India, but trying to move them to the US
        mvc.perform(put("/api/employees/" + indiaId).cookie(indiaHr).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json("asha@acme.test", "US", "USD", "130000")))
                .andExpect(status().isForbidden());
        assertThat(employees.findById(indiaId).orElseThrow().getCountry()).isEqualTo("IN");
    }

    @Test
    void aViewerCannotEdit() throws Exception {
        mvc.perform(put("/api/employees/" + usId).cookie(signedInAs("viewer@acme.test")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json("john@acme.test", "US", "USD", "130000")))
                .andExpect(status().isForbidden());
    }

    // ----- delete -----

    @Test
    void aSuperadminAndAManagerCanDelete() throws Exception {
        mvc.perform(delete("/api/employees/" + usId).cookie(signedInAs("manager@acme.test")).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete("/api/employees/" + indiaId).cookie(signedInAs("superadmin@acme.test")).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(employees.count()).isZero();
    }

    @Test
    void aRegionalOfficerAndAViewerCannotDelete() throws Exception {
        mvc.perform(delete("/api/employees/" + indiaId).cookie(signedInAs("hr.in@acme.test")).with(csrf()))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/employees/" + indiaId).cookie(signedInAs("viewer@acme.test")).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(employees.count()).isEqualTo(2);
    }

    @Test
    void deletingSomeoneWhoDoesNotExistIsNotFound() throws Exception {
        mvc.perform(delete("/api/employees/999999").cookie(signedInAs("manager@acme.test")).with(csrf()))
                .andExpect(status().isNotFound());
    }

    // ----- helpers -----

    private Cookie signedInAs(String email) throws Exception {
        String body = "{\"email\":\"" + email + "\",\"password\":\"Demo@12345\"}";
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("access_token");
    }

}
