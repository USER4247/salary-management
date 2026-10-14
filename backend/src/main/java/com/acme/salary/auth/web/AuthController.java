package com.acme.salary.auth.web;

import com.acme.salary.auth.ability.Ability;
import com.acme.salary.auth.ability.AbilityFactory;
import com.acme.salary.auth.ability.Rule;
import com.acme.salary.auth.security.AuthContext;
import com.acme.salary.auth.session.AuthenticationFailedException;
import com.acme.salary.auth.session.Session;
import com.acme.salary.auth.session.SessionService;
import com.acme.salary.auth.user.User;
import com.acme.salary.auth.user.UserRepository;
import com.acme.salary.auth.web.AuthDtos.LoginRequest;
import com.acme.salary.auth.web.AuthDtos.SessionResponse;
import com.acme.salary.auth.web.AuthDtos.UserView;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final SessionService sessions;
    private final AuthCookies cookies;
    private final AbilityFactory abilityFactory;
    private final AuthContext authContext;
    private final UserRepository users;

    AuthController(SessionService sessions, AuthCookies cookies, AbilityFactory abilityFactory,
                   AuthContext authContext, UserRepository users) {
        this.sessions = sessions;
        this.cookies = cookies;
        this.abilityFactory = abilityFactory;
        this.authContext = authContext;
        this.users = users;
    }

    @PostMapping("/login")
    ResponseEntity<?> login(@Valid @RequestBody LoginRequest request) {
        try {
            return signedIn(sessions.login(request.email(), request.password()));
        } catch (AuthenticationFailedException e) {
            return unauthorized(e);
        }
    }

    @PostMapping("/refresh")
    ResponseEntity<?> refresh(@CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        try {
            return signedIn(sessions.refresh(refreshToken));
        } catch (AuthenticationFailedException e) {
            return unauthorized(e);
        }
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(@CookieValue(name = AuthCookies.REFRESH, required = false) String refreshToken) {
        sessions.logout(refreshToken);
        return ResponseEntity.noContent().headers(cookies.clear()).build();
    }

    /** The current user and abilities, without issuing or rotating any token. */
    @GetMapping("/me")
    SessionResponse me() {
        Jwt jwt = authContext.currentJwt();
        User user = users.findById(Long.valueOf(jwt.getSubject()))
                .orElseThrow(() -> new AuthenticationFailedException("Invalid session"));
        return body(user, jwt.getExpiresAt(), authContext.currentAbility());
    }

    private ResponseEntity<SessionResponse> signedIn(Session session) {
        Ability ability = abilityFactory.forRole(session.user().getRole(), session.user().getCountryScope());
        return ResponseEntity.ok()
                .headers(cookies.issue(session))
                .body(body(session.user(), session.access().expiresAt(), ability));
    }

    private SessionResponse body(User user, Instant accessExpiresAt, Ability ability) {
        List<Map<String, Object>> rules = ability.rules().stream().map(Rule::toCaslRule).toList();
        return new SessionResponse(UserView.of(user), accessExpiresAt, rules);
    }

    /** A failed login or refresh also clears any stale cookies so the browser state matches the server. */
    private ResponseEntity<ProblemDetail> unauthorized(AuthenticationFailedException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, e.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).headers(cookies.clear()).body(problem);
    }
}
