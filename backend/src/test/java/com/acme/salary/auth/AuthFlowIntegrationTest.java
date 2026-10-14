package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.acme.salary.auth.AuthTestSupport.Cookies;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void loginSetsHttpOnlyStrictCookiesAndKeepsTokensOutOfTheBody() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@acme.test\",\"password\":\"" + AuthTestSupport.DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("HR_ADMIN"))
                .andExpect(jsonPath("$.accessExpiresAt").exists())
                .andExpect(jsonPath("$.rules[0].action").exists())
                .andReturn().getResponse();

        List<String> setCookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        String access = cookieHeader(setCookies, "access_token");
        String refresh = cookieHeader(setCookies, "refresh_token");
        assertThat(access).contains("HttpOnly", "SameSite=Strict", "Path=/");
        assertThat(refresh).contains("HttpOnly", "SameSite=Strict", "Path=/api/auth");
        assertThat(response.getContentAsString()).doesNotContain(response.getCookie("access_token").getValue());
        assertThat(response.getContentAsString()).doesNotContain(response.getCookie("refresh_token").getValue());
    }

    @Test
    void wrongPasswordAndUnknownUserGetTheSameResponse() throws Exception {
        String wrongPassword = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@acme.test\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();
        String unknownUser = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"ghost@acme.test\",\"password\":\"nope\"}"))
                .andExpect(status().isUnauthorized()).andReturn().getResponse().getContentAsString();

        assertThat(wrongPassword).isEqualTo(unknownUser);
    }

    @Test
    void blankCredentialsAreRejectedAsBadRequest() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void meReturnsTheUserAndCaslRulesForTheRole() throws Exception {
        Cookies cookies = AuthTestSupport.login(mvc, "india.hr@acme.test");

        mvc.perform(get("/api/auth/me").cookie(cookies.access()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value("india.hr@acme.test"))
                .andExpect(jsonPath("$.user.countryScope").value("IN"))
                .andExpect(jsonPath("$.rules[?(@.action=='read' && @.subject=='Employee')].conditions.country").value("IN"));
    }

    @Test
    void meWithoutOrWithGarbageCookieIsUnauthorized() throws Exception {
        mvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").cookie(new Cookie("access_token", "garbage")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRotatesTheTokenAndIssuesNewCookies() throws Exception {
        Cookies first = AuthTestSupport.login(mvc, "viewer@acme.test");

        MockHttpServletResponse response = mvc.perform(post("/api/auth/refresh").cookie(first.refresh()).with(csrf()))
                .andExpect(status().isOk())
                .andReturn().getResponse();

        assertThat(response.getCookie("refresh_token").getValue()).isNotEqualTo(first.refresh().getValue());
        assertThat(response.getCookie("access_token").getValue()).isNotBlank();
    }

    @Test
    void refreshWorksEvenWhenTheAccessCookieIsExpiredOrInvalid() throws Exception {
        Cookies cookies = AuthTestSupport.login(mvc, "viewer@acme.test");

        mvc.perform(post("/api/auth/refresh")
                        .cookie(new Cookie("access_token", "expired-or-garbage"), cookies.refresh())
                        .with(csrf()))
                .andExpect(status().isOk());
    }

    @Test
    void reusingARotatedRefreshTokenKillsTheWholeSession() throws Exception {
        Cookies first = AuthTestSupport.login(mvc, "admin@acme.test");
        Cookie rotated = mvc.perform(post("/api/auth/refresh").cookie(first.refresh()).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getCookie("refresh_token");

        // The attacker (or a stale tab) replays the old token...
        mvc.perform(post("/api/auth/refresh").cookie(first.refresh()).with(csrf()))
                .andExpect(status().isUnauthorized());
        // ...and the legitimate newest token is revoked too.
        mvc.perform(post("/api/auth/refresh").cookie(rotated).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWithoutCsrfTokenIsForbidden() throws Exception {
        Cookies cookies = AuthTestSupport.login(mvc, "viewer@acme.test");

        mvc.perform(post("/api/auth/refresh").cookie(cookies.refresh())).andExpect(status().isForbidden());
    }

    @Test
    void refreshWithoutCookieIsUnauthorizedAndClearsCookies() throws Exception {
        MockHttpServletResponse response = mvc.perform(post("/api/auth/refresh").with(csrf()))
                .andExpect(status().isUnauthorized()).andReturn().getResponse();

        assertThat(cookieHeader(response.getHeaders(HttpHeaders.SET_COOKIE), "refresh_token")).contains("Max-Age=0");
    }

    @Test
    void logoutRevokesTheRefreshTokenAndClearsTheCookies() throws Exception {
        Cookies cookies = AuthTestSupport.login(mvc, "viewer@acme.test");

        MockHttpServletResponse response = mvc.perform(post("/api/auth/logout").cookie(cookies.refresh()).with(csrf()))
                .andExpect(status().isNoContent()).andReturn().getResponse();

        assertThat(cookieHeader(response.getHeaders(HttpHeaders.SET_COOKIE), "access_token")).contains("Max-Age=0");
        assertThat(cookieHeader(response.getHeaders(HttpHeaders.SET_COOKIE), "refresh_token")).contains("Max-Age=0");
        mvc.perform(post("/api/auth/refresh").cookie(cookies.refresh()).with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }

    @Test
    void otherApiPathsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/anything")).andExpect(status().isUnauthorized());
    }

    private static String cookieHeader(List<String> setCookies, String name) {
        return setCookies.stream().filter(header -> header.startsWith(name + "=")).findFirst().orElseThrow();
    }
}
