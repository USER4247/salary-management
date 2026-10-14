package com.acme.salary.auth.ability;

public enum Subject {
    EMPLOYEE("Employee"),
    INSIGHTS("Insights");

    private final String wire;

    Subject(String wire) {
        this.wire = wire;
    }

    /** Name used in the CASL wire format shared with the UI. */
    public String wire() {
        return wire;
    }
}
