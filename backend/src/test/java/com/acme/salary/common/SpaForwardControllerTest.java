package com.acme.salary.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SpaForwardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void angularPagesAreAnsweredWithIndexHtmlEvenWhenNotSignedIn() throws Exception {
        for (String page : new String[] {"/sign-in", "/access", "/insights"}) {
            mvc.perform(get(page)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void theApiIsNotForwardedAndStillNeedsASignIn() throws Exception {
        mvc.perform(get("/api/insights/pay")).andExpect(status().isUnauthorized());
    }
}
