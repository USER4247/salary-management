package com.acme.salary.employee;

import com.acme.salary.auth.Ability;
import com.acme.salary.auth.Ability.Action;
import com.acme.salary.auth.Ability.Subject;
import com.acme.salary.common.FieldException;
import com.acme.salary.insights.FxRates;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Finds employees for the list screen: search and filters, limited to what the caller may see. */
@Service
public class EmployeeService {

    private static final Logger log = LoggerFactory.getLogger(EmployeeService.class);

    /** What the user typed into the search box and filters. Any of them may be null (= not used). */
    public record Filters(String search, String country, String department, String status) {
    }

    /** Everything the create and edit forms send. */
    public record EmployeeData(String fullName, String email, String country, String department, String jobTitle,
                               long annualSalary, String currency, LocalDate hireDate, String status) {
    }

    private final EmployeeRepository employees;
    private final FxRates fx;

    public EmployeeService(EmployeeRepository employees, FxRates fx) {
        this.employees = employees;
        this.fx = fx;
    }

    /** One employee, if the caller is allowed to see them. */
    @Transactional(readOnly = true)
    public Employee get(long id, Ability ability) {
        return visible(id, ability);
    }

    @Transactional   // no readOnly: this writes, so it uses the single writer connection
    public Employee create(EmployeeData data, Ability ability) {
        // A regional officer may only add people to their own country.
        requireAllowed(ability, Action.CREATE, data.country(), "add employees to " + data.country());
        checkRules(data, null);
        Employee saved = employees.save(new Employee(data.fullName().trim(), data.email().trim(), data.country(),
                data.department().trim(), data.jobTitle().trim(), data.annualSalary(), data.currency(),
                data.hireDate().toString(), data.status()));
        log.info("Employee {} created in {}", saved.getId(), saved.getCountry());
        return saved;
    }

    @Transactional
    public Employee update(long id, EmployeeData data, Ability ability) {
        Employee employee = visible(id, ability);
        requireAllowed(ability, Action.UPDATE, employee.getCountry(), "change employees in " + employee.getCountry());
        // Also check the NEW country, otherwise a regional officer could "move" someone out of their region.
        requireAllowed(ability, Action.UPDATE, data.country(), "move employees to " + data.country());
        checkRules(data, id);
        employee.update(data.fullName().trim(), data.email().trim(), data.country(), data.department().trim(),
                data.jobTitle().trim(), data.annualSalary(), data.currency(), data.hireDate().toString(), data.status());
        log.info("Employee {} updated", id);
        return employee;   // saved automatically when the transaction ends
    }

    @Transactional
    public void delete(long id, Ability ability) {
        Employee employee = visible(id, ability);
        requireAllowed(ability, Action.DELETE, employee.getCountry(), "delete employees in " + employee.getCountry());
        employees.delete(employee);
        log.info("Employee {} deleted", id);
    }

    /**
     * Loads an employee the caller may read. For someone outside their region the answer is the same "not found" as
     * for an id that does not exist, so a regional officer cannot find out who works elsewhere.
     */
    private Employee visible(long id, Ability ability) {
        return employees.findById(id)
                .filter(employee -> ability.can(Action.READ, Subject.EMPLOYEE, Map.of("country", employee.getCountry())))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employee not found"));
    }

    private static void requireAllowed(Ability ability, Action action, String country, String what) {
        if (!ability.can(action, Subject.EMPLOYEE, Map.of("country", country))) {
            throw new AccessDeniedException("You are not allowed to " + what);
        }
    }

    /** Rules that need the database or the exchange-rate table, so they cannot be written on the request class. */
    private void checkRules(EmployeeData data, Long existingId) {
        if (!fx.supports(data.currency())) {
            // Without a rate the employee would silently vanish from the insights, so refuse it here.
            throw new FieldException(HttpStatus.BAD_REQUEST, "currency", "Unsupported currency " + data.currency());
        }
        boolean emailTaken = existingId == null
                ? employees.existsByEmailIgnoreCase(data.email().trim())
                : employees.existsByEmailIgnoreCaseAndIdNot(data.email().trim(), existingId);
        if (emailTaken) {
            throw new FieldException(HttpStatus.CONFLICT, "email", "Another employee already uses this email");
        }
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
