package com.acme.salary.auth;

import com.acme.salary.auth.Ability.Action;
import com.acme.salary.auth.Ability.Subject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Policy guard. Put on an endpoint: the caller's ability must allow this action on this subject,
 * e.g. {@code @CheckAbility(action = UPDATE, subject = EMPLOYEE)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface CheckAbility {

    Action action();

    Subject subject();
}
