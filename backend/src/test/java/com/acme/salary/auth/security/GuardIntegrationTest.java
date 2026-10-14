package com.acme.salary.auth.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.salary.auth.AuthTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The access guard (@RequireRole) and policy guard (@CheckAbility) against real signed-in users. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuardIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private Cookie accessFor(String email) throws Exception {
        return AuthTestSupport.login(mvc, email).access();
    }

    @Test
    void unauthenticatedCallsAreRejectedWith401() throws Exception {
        mvc.perform(get("/api/probe/employees")).andExpect(status().isUnauthorized());
    }

    @Test
    void adminPassesEveryPolicyAndTheRoleGuard() throws Exception {
        Cookie admin = accessFor("admin@acme.test");

        mvc.perform(get("/api/probe/employees").cookie(admin)).andExpect(status().isOk());
        mvc.perform(post("/api/probe/employees").cookie(admin).with(csrf())).andExpect(status().isOk());
        mvc.perform(delete("/api/probe/employees/1").cookie(admin).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/probe/admin-only").cookie(admin)).andExpect(status().isOk());
    }

    @Test
    void viewerCanReadButNotWrite() throws Exception {
        Cookie viewer = accessFor("viewer@acme.test");

        mvc.perform(get("/api/probe/employees").cookie(viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/probe/employees").cookie(viewer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/probe/employees/1").cookie(viewer).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void roleGuardRejectsRolesThatAreNotListed() throws Exception {
        mvc.perform(get("/api/probe/admin-only").cookie(accessFor("viewer@acme.test"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/probe/admin-only").cookie(accessFor("india.hr@acme.test"))).andExpect(status().isForbidden());
    }

    @Test
    void countryHrPassesTheTypeCheckButIsLimitedToTheirCountryPerResource() throws Exception {
        Cookie indiaHr = accessFor("india.hr@acme.test");

        mvc.perform(put("/api/probe/employees/in/IN").cookie(indiaHr).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/probe/employees/in/US").cookie(indiaHr).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/probe/employees/1").cookie(indiaHr).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void writesWithoutACsrfTokenAreForbiddenEvenForAdmins() throws Exception {
        mvc.perform(post("/api/probe/employees").cookie(accessFor("admin@acme.test")))
                .andExpect(status().isForbidden());
    }
}
