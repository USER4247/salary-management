package com.acme.salary.common;

import org.springframework.http.HttpStatus;

/** A problem with one specific input field, so the screen can show the message right under that field. */
public class FieldException extends RuntimeException {

    private final HttpStatus status;
    private final String field;

    public FieldException(HttpStatus status, String field, String message) {
        super(message);
        this.status = status;
        this.field = field;
    }

    public HttpStatus status() {
        return status;
    }

    public String field() {
        return field;
    }
}
