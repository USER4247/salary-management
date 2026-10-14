package com.acme.salary.auth.security;

import com.acme.salary.auth.web.AuthCookies;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AuthenticationEntryPointFailureHandler;
import org.springframework.security.web.authentication.AuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Authentication guard: stateless, cookie-carried JWT, with cookie-based CSRF protection for the SPA.
 *
 * <p>The JWT is validated by an explicit {@link AuthenticationFilter} rather than Spring's
 * {@code oauth2ResourceServer()} DSL on purpose: that DSL exempts every request carrying a bearer token from CSRF
 * checks, which is right for an Authorization header but would silently disable CSRF protection for a token that
 * the browser attaches by itself as a cookie.
 */
@Configuration
@EnableWebSecurity
class SecurityConfig {

    /** Endpoints that must work with no, or an already expired, access token. */
    private static final Set<String> TOKENLESS_AUTH_PATHS = Set.of("/api/auth/login", "/api/auth/refresh", "/api/auth/logout");

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder) throws Exception {
        CookieCsrfTokenRepository csrfTokens = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfTokens.setCookieCustomizer(cookie -> cookie.sameSite("Strict"));
        AuthenticationEntryPoint unauthorized = new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED);

        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokens)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                        // Login needs credentials and has no session to hijack; everything else is protected.
                        .ignoringRequestMatchers("/api/auth/login"))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .addFilterBefore(cookieJwtFilter(jwtDecoder, unauthorized), AuthorizationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling.authenticationEntryPoint(unauthorized))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers("/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/api/**").authenticated()
                        .anyRequest().permitAll());
        return http.build();
    }

    private AuthenticationFilter cookieJwtFilter(JwtDecoder jwtDecoder, AuthenticationEntryPoint unauthorized) {
        JwtAuthenticationProvider provider = new JwtAuthenticationProvider(jwtDecoder);
        provider.setJwtAuthenticationConverter(jwtAuthenticationConverter());
        AuthenticationManager manager = new ProviderManager(provider);

        AuthenticationFilter filter = new AuthenticationFilter(manager, SecurityConfig::tokenFromCookie);
        // Skipped for the token-less endpoints: otherwise an expired cookie would be rejected with 401
        // before the refresh endpoint could replace it.
        filter.setRequestMatcher(request -> !TOKENLESS_AUTH_PATHS.contains(pathWithinApp(request)));
        // The default success handler redirects like a form login; an API must just continue the request.
        filter.setSuccessHandler((request, response, authentication) -> { });
        filter.setFailureHandler(new AuthenticationEntryPointFailureHandler(unauthorized));
        return filter;
    }

    /** No cookie means no authentication attempt (the request continues as anonymous). */
    private static BearerTokenAuthenticationToken tokenFromCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (Cookie cookie : request.getCookies()) {
            if (AuthCookies.ACCESS.equals(cookie.getName()) && !cookie.getValue().isBlank()) {
                return new BearerTokenAuthenticationToken(cookie.getValue());
            }
        }
        return null;
    }

    private static String pathWithinApp(HttpServletRequest request) {
        return request.getRequestURI().substring(request.getContextPath().length());
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName("role");
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
