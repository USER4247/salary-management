package com.acme.salary.auth.ability;

import java.util.Locale;

public enum Action {
    READ,
    CREATE,
    UPDATE,
    DELETE;

    /** Lower-case name used in the CASL wire format shared with the UI. */
    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }
}
