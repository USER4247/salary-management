package com.acme.salary.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Runs just before a controller method and enforces {@link RequireRole} and {@link CheckAbility}.
 *
 * <p>By the time we get here the JWT filter has already worked out who the caller is. This class only decides
 * whether they are allowed to do this particular thing. If not, the AccessDeniedException becomes a 403.
 */
@Component
class GuardInterceptor implements HandlerInterceptor, WebMvcConfigurer {

    private static final Logger log = LoggerFactory.getLogger(GuardInterceptor.class);

    private final AuthContext authContext;

    GuardInterceptor(AuthContext authContext) {
        this.authContext = authContext;
    }

    /** Applies this guard to every /api endpoint. */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(this).addPathPatterns("/api/**");
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;   // not a controller method (e.g. a static file): nothing to guard
        }

        RequireRole requireRole = find(method, RequireRole.class);
        if (requireRole != null) {
            Role role = authContext.currentRole();
            boolean allowed = role == Role.SUPERADMIN || Arrays.asList(requireRole.value()).contains(role);
            if (!allowed) {
                log.warn("Denied: role {} may not call {}", role, request.getRequestURI());
                throw new AccessDeniedException("Role " + role + " is not allowed here");
            }
        }

        CheckAbility checkAbility = find(method, CheckAbility.class);
        if (checkAbility != null && !authContext.currentAbility().can(checkAbility.action(), checkAbility.subject())) {
            log.warn("Denied: {} {} on {}", authContext.currentRole(), checkAbility.action(), request.getRequestURI());
            throw new AccessDeniedException("Not allowed to " + checkAbility.action().wire() + " " + checkAbility.subject().wire());
        }
        return true;
    }

    /** Looks for the annotation on the method first, then on its controller class. */
    private static <A extends Annotation> A find(HandlerMethod method, Class<A> type) {
        A onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), type);
        return onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), type);
    }
}
