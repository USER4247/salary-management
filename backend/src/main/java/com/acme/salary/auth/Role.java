package com.acme.salary.auth;

/** The kinds of user. A role is only a label; what it allows is decided in {@link Ability#forRole}. */
public enum Role {
    SUPERADMIN,   // absolute access to everything
    HR_MANAGER,   // full control of employees, can read insights
    HR_VIEWER,    // read-only
    COUNTRY_HR    // like a manager, but only for employees in their own country
}
