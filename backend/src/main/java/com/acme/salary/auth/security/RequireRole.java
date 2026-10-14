package com.acme.salary.auth.security;

import com.acme.salary.auth.ability.Role;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Access guard: only the listed roles may call the annotated handler (or any handler in the annotated class). */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface RequireRole {

    Role[] value();
}
