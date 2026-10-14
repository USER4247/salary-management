package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Reads the log lines the filter writes, using an in-memory log appender. */
class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();
    private final Logger apiLogger = (Logger) LoggerFactory.getLogger("api-requests");

    @BeforeEach
    void startCapturing() {
        logs.start();
        apiLogger.addAppender(logs);
    }

    @AfterEach
    void stopCapturing() {
        apiLogger.detachAppender(logs);
    }

    @Test
    void logsMethodPathStatusAndWhoCalled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/me");
        request.setRemoteAddr("10.0.0.5");
        request.setAttribute(RequestLogFilter.USER_ATTRIBUTE, "42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(200);

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(logs.list).hasSize(1);
        assertThat(logs.list.get(0).getFormattedMessage())
                .startsWith("API GET /api/auth/me -> 200 in ")
                .endsWith("(user=42, ip=10.0.0.5)");
    }

    @Test
    void anonymousCallsAreMarkedAsSuch() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(401);

        filter.doFilter(new MockHttpServletRequest("POST", "/api/auth/login"), response, new MockFilterChain());

        assertThat(logs.list.get(0).getFormattedMessage()).contains("-> 401").contains("user=anonymous");
    }

    @Test
    void neverLogsTheQueryStringOrCookies() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/employees");
        request.setQueryString("token=secret");
        request.setCookies(new jakarta.servlet.http.Cookie("access_token", "super-secret-jwt"));

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(logs.list.get(0).getFormattedMessage()).doesNotContain("secret");
    }

    @Test
    void ignoresPathsOutsideTheApi() throws Exception {
        filter.doFilter(new MockHttpServletRequest("GET", "/index.html"), new MockHttpServletResponse(), new MockFilterChain());

        assertThat(logs.list).isEmpty();
    }
}
