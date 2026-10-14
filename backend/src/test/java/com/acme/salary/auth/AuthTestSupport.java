package com.acme.salary.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/** Signs in through the real login endpoint and returns the cookies a browser would now hold. */
final class AuthTestSupport {

    static final String DEMO_PASSWORD = "Demo@12345";

    private AuthTestSupport() {
    }

    record Cookies(Cookie access, Cookie refresh) {
    }

    static Cookies login(MockMvc mvc, String email) throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse();
        return new Cookies(response.getCookie("access_token"), response.getCookie("refresh_token"));
    }
}
