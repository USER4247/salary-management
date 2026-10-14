package com.acme.salary.auth.security;

import com.acme.salary.auth.ability.Role;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Evaluates {@link RequireRole} and {@link CheckAbility} before a handler runs. Authentication has already
 * happened in the security filter chain, so reaching this point means the caller is who the token says;
 * here we only decide what they may do. A failed check becomes a 403 via Spring Security's handler.
 */
@Component
class GuardInterceptor implements HandlerInterceptor {

    private final AuthContext authContext;

    GuardInterceptor(AuthContext authContext) {
        this.authContext = authContext;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod method)) {
            return true;
        }
        RequireRole requireRole = annotation(method, RequireRole.class);
        if (requireRole != null) {
            Role role = authContext.currentRole();
            if (Arrays.stream(requireRole.value()).noneMatch(allowed -> allowed == role)) {
                throw new AccessDeniedException("Role " + role + " is not allowed here");
            }
        }
        CheckAbility checkAbility = annotation(method, CheckAbility.class);
        if (checkAbility != null && !authContext.currentAbility().can(checkAbility.action(), checkAbility.subject())) {
            throw new AccessDeniedException(
                    "Not allowed to " + checkAbility.action().wire() + " " + checkAbility.subject().wire());
        }
        return true;
    }

    private static <A extends java.lang.annotation.Annotation> A annotation(HandlerMethod method, Class<A> type) {
        A onMethod = AnnotatedElementUtils.findMergedAnnotation(method.getMethod(), type);
        return onMethod != null ? onMethod : AnnotatedElementUtils.findMergedAnnotation(method.getBeanType(), type);
    }
}
