package com.acme.salary.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Unit tests with a hand-made clock, so "a minute later" needs no waiting. */
class RateLimitFilterTest {

    private final MutableClock clock = new MutableClock();

    @Test
    void requestsUnderTheLimitPass() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(3, 100, clock);

        for (int i = 0; i < 3; i++) {
            assertThat(call(filter, "GET", "/api/employees", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    @Test
    void theRequestOverTheLimitGets429WithRetryAfter() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(3, 100, clock);
        for (int i = 0; i < 3; i++) {
            call(filter, "GET", "/api/employees", "1.1.1.1");
        }

        MockHttpServletResponse blocked = call(filter, "GET", "/api/employees", "1.1.1.1");

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(Long.parseLong(blocked.getHeader("Retry-After"))).isBetween(1L, 60L);
        assertThat(blocked.getContentAsString()).contains("Too Many Requests");
        assertThat(blocked.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
    }

    @Test
    void eachIpHasItsOwnLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 100, clock);
        call(filter, "GET", "/api/employees", "1.1.1.1");

        assertThat(call(filter, "GET", "/api/employees", "1.1.1.1").getStatus()).isEqualTo(429);
        assertThat(call(filter, "GET", "/api/employees", "2.2.2.2").getStatus()).isEqualTo(200);
    }

    @Test
    void loginHasItsOwnStricterLimit() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(100, 2, clock);
        call(filter, "POST", "/api/auth/login", "1.1.1.1");
        call(filter, "POST", "/api/auth/login", "1.1.1.1");

        assertThat(call(filter, "POST", "/api/auth/login", "1.1.1.1").getStatus()).isEqualTo(429);
        assertThat(call(filter, "GET", "/api/employees", "1.1.1.1").getStatus()).isEqualTo(200);   // other calls fine
    }

    @Test
    void theCountResetsAfterAMinute() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 100, clock);
        call(filter, "GET", "/api/employees", "1.1.1.1");
        assertThat(call(filter, "GET", "/api/employees", "1.1.1.1").getStatus()).isEqualTo(429);

        clock.advanceSeconds(61);

        assertThat(call(filter, "GET", "/api/employees", "1.1.1.1").getStatus()).isEqualTo(200);
    }

    @Test
    void nonApiPathsAreNeverLimited() throws Exception {
        RateLimitFilter filter = new RateLimitFilter(1, 1, clock);

        for (int i = 0; i < 5; i++) {
            assertThat(call(filter, "GET", "/index.html", "1.1.1.1").getStatus()).isEqualTo(200);
        }
    }

    private static MockHttpServletResponse call(RateLimitFilter filter, String method, String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());   // MockFilterChain leaves the status at 200
        return response;
    }

    /** A clock we can move forward by hand. */
    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T10:00:00Z");

        void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
