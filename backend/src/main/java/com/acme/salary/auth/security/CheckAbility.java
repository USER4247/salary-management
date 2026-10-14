package com.acme.salary.auth.security;

import com.acme.salary.auth.ability.Action;
import com.acme.salary.auth.ability.Subject;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Policy guard: the caller's ability must allow {@code action} on {@code subject}. This is the type-level
 * check; handlers that load a concrete resource should additionally check it through {@link AuthContext}.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CheckAbility {

    Action action();

    Subject subject();
}
