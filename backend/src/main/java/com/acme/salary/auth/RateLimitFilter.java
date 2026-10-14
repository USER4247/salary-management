package com.acme.salary.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A first line of defence against one IP address hammering the API (brute-forcing logins, scraping, simple floods).
 *
 * <p>It counts requests per IP in a fixed one-minute window. Over the limit, the request is refused with
 * "429 Too Many Requests" and a Retry-After header, before any real work (passwords, database) is done.
 * Logins get a much smaller limit than other calls because guessing passwords is the main thing to slow down.
 *
 * <p>Honest limits: counters live in this server's memory, so it is per instance and resets on restart, and a
 * large distributed attack needs a CDN or gateway in front. Behind a reverse proxy, make sure the proxy's
 * forwarded address is what {@code getRemoteAddr()} reports, otherwise everyone looks like one IP.
 */
class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);
    private static final long WINDOW_MILLIS = 60_000;
    private static final int CLEANUP_THRESHOLD = 10_000;

    /** How many requests one key made in its current window. */
    private static final class Counter {
        private long windowStart;
        private int count;

        /** Records one request. Returns 0 if allowed, or the seconds to wait if the limit is exceeded. */
        synchronized long hit(long now, int limit) {
            if (now - windowStart >= WINDOW_MILLIS) {   // a new minute has started: start counting again
                windowStart = now;
                count = 0;
            }
            count++;
            return count <= limit ? 0 : Math.max(1, (windowStart + WINDOW_MILLIS - now + 999) / 1000);
        }

        synchronized boolean isExpired(long now) {
            return now - windowStart >= WINDOW_MILLIS;
        }
    }

    private final ConcurrentHashMap<String, Counter> counters = new ConcurrentHashMap<>();
    private final int requestsPerMinute;
    private final int loginPerMinute;
    private final Clock clock;

    RateLimitFilter(int requestsPerMinute, int loginPerMinute, Clock clock) {
        this.requestsPerMinute = requestsPerMinute;
        this.loginPerMinute = loginPerMinute;
        this.clock = clock;
    }

    /** Only API calls are limited; the Angular app's own files are not. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long now = clock.millis();
        String ip = request.getRemoteAddr();

        long wait = hit(ip + "|all", requestsPerMinute, now);
        if (wait == 0 && isLogin(request)) {
            wait = hit(ip + "|login", loginPerMinute, now);   // logins also count against the stricter login limit
        }

        if (wait > 0) {
            log.warn("Rate limit hit by {} on {} {}; asked to wait {}s", ip, request.getMethod(), request.getRequestURI(), wait);
            response.setStatus(429);
            response.setHeader("Retry-After", String.valueOf(wait));
            response.setContentType("application/problem+json;charset=UTF-8");   // say UTF-8 explicitly; the default was ISO-8859-1
            response.getWriter().write("{\"title\":\"Too Many Requests\",\"status\":429,\"detail\":\"Slow down and retry in "
                    + wait + " seconds\"}");
            return;
        }
        chain.doFilter(request, response);
    }

    private long hit(String key, int limit, long now) {
        if (counters.size() > CLEANUP_THRESHOLD) {
            counters.values().removeIf(counter -> counter.isExpired(now));   // keep memory from growing forever
        }
        return counters.computeIfAbsent(key, k -> new Counter()).hit(now, limit);
    }

    private static boolean isLogin(HttpServletRequest request) {
        return "POST".equals(request.getMethod()) && "/api/auth/login".equals(request.getRequestURI());
    }
}
