package com.acme.salary.auth;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** The role guard (@RequireRole) and ability guard (@CheckAbility) tested with real signed-in users. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GuardIntegrationTest {

    @Autowired
    private MockMvc mvc;

    private Cookie signedInAs(String email) throws Exception {
        return AuthTestSupport.login(mvc, email).access();
    }

    @Test
    void notSignedInIs401() throws Exception {
        mvc.perform(get("/api/probe/employees")).andExpect(status().isUnauthorized());
    }

    @Test
    void superadminPassesEverything() throws Exception {
        Cookie superadmin = signedInAs("superadmin@acme.test");

        mvc.perform(get("/api/probe/employees").cookie(superadmin)).andExpect(status().isOk());
        mvc.perform(delete("/api/probe/employees/1").cookie(superadmin).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/probe/managers-only").cookie(superadmin)).andExpect(status().isOk());   // SUPERADMIN always passes @RequireRole
    }

    @Test
    void managerManagesEmployeesAndPassesTheirRoleGuard() throws Exception {
        Cookie manager = signedInAs("manager@acme.test");

        mvc.perform(post("/api/probe/employees").cookie(manager).with(csrf())).andExpect(status().isOk());
        mvc.perform(delete("/api/probe/employees/1").cookie(manager).with(csrf())).andExpect(status().isOk());
        mvc.perform(get("/api/probe/managers-only").cookie(manager)).andExpect(status().isOk());
    }

    @Test
    void viewerCanReadButNotWrite() throws Exception {
        Cookie viewer = signedInAs("viewer@acme.test");

        mvc.perform(get("/api/probe/employees").cookie(viewer)).andExpect(status().isOk());
        mvc.perform(post("/api/probe/employees").cookie(viewer).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/probe/employees/1").cookie(viewer).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void roleGuardRejectsRolesThatAreNotListed() throws Exception {
        mvc.perform(get("/api/probe/managers-only").cookie(signedInAs("viewer@acme.test"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/probe/managers-only").cookie(signedInAs("india.hr@acme.test"))).andExpect(status().isForbidden());
    }

    @Test
    void countryHrIsLimitedToTheirOwnCountry() throws Exception {
        Cookie indiaHr = signedInAs("india.hr@acme.test");

        mvc.perform(put("/api/probe/employees/in/IN").cookie(indiaHr).with(csrf())).andExpect(status().isOk());
        mvc.perform(put("/api/probe/employees/in/US").cookie(indiaHr).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(delete("/api/probe/employees/1").cookie(indiaHr).with(csrf())).andExpect(status().isForbidden());
    }

    @Test
    void writesWithoutACsrfTokenAreForbiddenEvenForSuperadmin() throws Exception {
        mvc.perform(post("/api/probe/employees").cookie(signedInAs("superadmin@acme.test")))
                .andExpect(status().isForbidden());
    }
}
