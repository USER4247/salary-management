package com.acme.salary.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Writes one line to the server log for every API call, so you can see what the client is asking for:
 *
 * <pre>API GET /api/auth/me -> 200 in 4 ms (user=3, ip=127.0.0.1)</pre>
 *
 * <p>Only the method, path, status, duration, caller and IP are logged. Never request bodies, query strings or
 * cookies, because those can hold passwords and tokens. It runs first in the filter chain, so calls that are
 * refused (401, 403, 429) show up too.
 */
class RequestLogFilter extends OncePerRequestFilter {

    /** The JWT filter stores the caller's id under this name once the token has been checked. */
    static final String USER_ATTRIBUTE = "authenticatedUserId";

    private static final Logger log = LoggerFactory.getLogger("api-requests");

    /** Only API calls are logged; the Angular app's own files would just be noise. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long start = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            long millis = (System.nanoTime() - start) / 1_000_000;
            Object user = request.getAttribute(USER_ATTRIBUTE);
            log.info("API {} {} -> {} in {} ms (user={}, ip={})", request.getMethod(), request.getRequestURI(),
                    response.getStatus(), millis, user != null ? user : "anonymous", request.getRemoteAddr());
        }
    }
}
