package com.acme.salary.employee;

import com.acme.salary.auth.Ability;
import com.acme.salary.auth.Ability.Action;
import com.acme.salary.auth.Ability.Subject;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Finds employees for the list screen: search and filters, limited to what the caller may see. */
@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    /** What the user typed into the search box and filters. Any of them may be null (= not used). */
    public record Filters(String search, String country, String department, String status) {
    }

    private final EmployeeRepository employees;

    public EmployeeService(EmployeeRepository employees) {
        this.employees = employees;
    }

    /** readOnly = true sends this to the pool of read-only connections (see DatabaseConfig). */
    @Transactional(readOnly = true)
    public Page<Employee> find(Filters filters, Pageable pageable, Ability ability) {
        List<Specification<Employee>> conditions = new ArrayList<>();

        // 1. What the caller is ALLOWED to see. Applied in the database query, so nothing else is ever loaded.
        Optional<Set<String>> allowedCountries = ability.countryLimit(Action.READ, Subject.EMPLOYEE);
        allowedCountries.ifPresent(countries -> conditions.add((employee, query, cb) -> employee.get("country").in(countries)));

        // 2. What the caller ASKED for (search box and filters). These can only narrow the result further.
        if (hasText(filters.country())) {
            conditions.add((employee, query, cb) -> cb.equal(employee.get("country"), filters.country()));
        }
        if (hasText(filters.department())) {
            conditions.add((employee, query, cb) -> cb.equal(employee.get("department"), filters.department()));
        }
        if (hasText(filters.status())) {
            conditions.add((employee, query, cb) -> cb.equal(employee.get("status"), filters.status()));
        }
        if (hasText(filters.search())) {
            String pattern = "%" + escapeLike(filters.search().trim().toLowerCase()) + "%";
            conditions.add((employee, query, cb) -> cb.or(
                    cb.like(cb.lower(employee.get("fullName")), pattern, '\\'),
                    cb.like(cb.lower(employee.get("email")), pattern, '\\'),
                    cb.like(cb.lower(employee.get("jobTitle")), pattern, '\\')));
        }

        log.debug("Employee search {} limited to {} -> page {}", filters, allowedCountries.map(Object::toString).orElse("all countries"),
                pageable.getPageNumber());
        return employees.findAll(Specification.allOf(conditions), pageable);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    /** In a LIKE pattern, % and _ are wildcards. Escape them so that a search for "50%" finds "50%". */
    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
