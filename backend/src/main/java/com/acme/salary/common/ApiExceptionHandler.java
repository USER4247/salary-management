package com.acme.salary.common;

import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns input problems into the same JSON shape every time:
 * <pre>{ "title": "...", "status": 400, "detail": "...", "errors": { "email": "must be a valid email" } }</pre>
 * so the Angular forms can put each message under the right field.
 */
@RestControllerAdvice
class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /** A request body that broke the rules written on the request class (@NotBlank, @Email, ...). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> invalidBody(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return problem(HttpStatus.BAD_REQUEST, "Please correct the highlighted fields", errors);
    }

    /** A rule the code checks itself, such as "this email is already used". */
    @ExceptionHandler(FieldException.class)
    ResponseEntity<ProblemDetail> field(FieldException ex) {
        return problem(ex.status(), ex.getMessage(), Map.of(ex.field(), ex.getMessage()));
    }

    /** Safety net: two people saving the same email at the same moment get a clean 409 instead of a 500. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> integrity(DataIntegrityViolationException ex) {
        log.warn("Database rule broken: {}", ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, "That change conflicts with existing data", Map.of());
    }

    private static ResponseEntity<ProblemDetail> problem(HttpStatus status, String detail, Map<String, String> errors) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty("errors", errors);
        return ResponseEntity.status(status).body(problem);
    }
}
