package com.acme.salary.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Set;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Security setup: every request passes through these filters, in this order:
 * <ol>
 *   <li>rate limit (too many requests from one IP are turned away);</li>
 *   <li>CSRF check (blocks other websites from acting as the user);</li>
 *   <li>JWT check (reads the access token cookie and works out who is calling).</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    /** These endpoints must work without a valid access token (you cannot have one before logging in). */
    private static final Set<String> TOKENLESS_PATHS = Set.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    // ---------- the filter chain ----------

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, Clock clock,
            @Value("${app.rate-limit.requests-per-minute}") int requestsPerMinute,
            @Value("${app.rate-limit.login-per-minute}") int loginPerMinute) throws Exception {

        // If someone is not signed in, answer 401 (the Angular app reacts to that), not a login page redirect.
        AuthenticationEntryPoint unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);

        // CSRF token lives in a cookie called XSRF-TOKEN. Angular copies it into the X-XSRF-TOKEN header
        // on every POST/PUT/DELETE by itself, so the two sides match without any extra code.
        CookieCsrfTokenRepository csrfCookie = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfCookie.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfCookie)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Login has no session to hijack yet (it needs the password), so it is exempt.
                        .ignoringRequestMatchers("/api/auth/login"))
                // Both run before everything else, in this order: first log the call, then maybe turn it away.
                .addFilterBefore(new RequestLogFilter(), CsrfFilter.class)
                .addFilterBefore(new RateLimitFilter(requestsPerMinute, loginPerMinute, clock), CsrfFilter.class)
                .addFilterAfter(csrfCookieWriter(), BasicAuthenticationFilter.class)
                .addFilterBefore(jwtCookieFilter(jwtDecoder, unauthorized), AuthorizationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(unauthorized))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/api/**").authenticated()   // everything else under /api needs a sign-in
                        .anyRequest().permitAll());                   // the Angular app's own files
        return http.build();
    }

    /**
     * Reads the access token from its cookie and checks it.
     *
     * <p>We do this with a plain filter on purpose. Spring's ready-made oauth2ResourceServer() setup treats every
     * request that carries a token as exempt from CSRF checks. That is right for an "Authorization" header (a
     * browser never adds one by itself) but wrong for a cookie (a browser adds it automatically, which is exactly
     * what CSRF abuses). A test caught this: a cookie-authenticated POST without a CSRF token was accepted.
     */
    private AuthenticationFilter jwtCookieFilter(JwtDecoder jwtDecoder, AuthenticationEntryPoint unauthorized) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter());

        AuthenticationFilter filter = new AuthenticationFilter(new ProviderManager(provider), SecurityConfig::tokenFromCookie);
        // Skip login/refresh/logout: an expired cookie must not block the very call that replaces it.
        filter.setRequestMatcher(request -> !TOKENLESS_PATHS.contains(pathOf(request)));
        // After a successful check just carry on with the request (the default would redirect like a login form).
        // We also note who is calling, so the request log can print it.
        filter.setSuccessHandler((request, response, authentication) ->
                request.setAttribute(RequestLogFilter.USER_ATTRIBUTE, authentication.getName()));
        filter.setFailureHandler(new AuthenticationEntryPointFailureHandler(unauthorized));
        return filter;
    }

    /** No cookie = no attempt to sign in; the request simply continues as anonymous and may be refused later. */
    private static BearerTokenAuthenticationToken tokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (AuthController.ACCESS_COOKIE.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return new BearerTokenAuthenticationToken(cookie.getValue());
            }
        }
        return null;
    }

    private static String pathOf(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    /** Turns the "role" claim of the token into a Spring authority such as ROLE_SUPERADMIN. */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * Spring creates the CSRF token lazily, so on a plain GET the XSRF-TOKEN cookie would never be sent.
     * Asking for the token here forces the cookie onto every response, ready for Angular to use.
     */
    private OncePerRequestFilter csrfCookieWriter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                    throws java.io.IOException, jakarta.servlet.ServletException {
                CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
                if (token != null) {
                    token.getToken();
                }
                chain.doFilter(request, response);
            }
        };
    }

    // ---------- small building blocks ----------

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();   // slow on purpose, which makes password guessing expensive
    }

    /** The secret that signs the tokens. It comes from the JWT_SECRET environment variable, never from code. */
    @Bean
    SecretKey jwtKey(@Value("${app.auth.jwt-secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 characters long");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKey jwtKey) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(jwtKey));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKey jwtKey) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(jwtKey).macAlgorithm(MacAlgorithm.HS256).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("salary-management"));  // also checks expiry
        return decoder;
    }
}
