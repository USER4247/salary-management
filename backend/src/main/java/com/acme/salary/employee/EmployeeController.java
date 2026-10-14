package com.acme.salary.employee;

import com.acme.salary.auth.Ability.Action;
import com.acme.salary.auth.Ability.Subject;
import com.acme.salary.auth.AuthContext;
import com.acme.salary.auth.CheckAbility;
import com.acme.salary.employee.EmployeeService.EmployeeData;
import com.acme.salary.employee.EmployeeService.Filters;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Employees: list (paged), read one, create, edit and delete. Every endpoint first passes the policy guard
 * ({@code @CheckAbility}: may this role do this at all?) and then the service checks the specific employee's country.
 */
@RestController
@RequestMapping("/api/employees")
class EmployeeController {

    private static final int MAX_PAGE_SIZE = 100;
    /** Only these columns may be sorted on. Never pass the sort text straight to the database layer. */
    private static final Set<String> SORTABLE = Set.of(
            "id", "fullName", "email", "country", "department", "jobTitle", "annualSalary", "hireDate", "status");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "ON_LEAVE", "TERMINATED");

    record EmployeeView(Long id, String fullName, String email, String country, String department, String jobTitle,
                        long annualSalary, String currency, String hireDate, String status) {
        static EmployeeView of(Employee e) {
            return new EmployeeView(e.getId(), e.getFullName(), e.getEmail(), e.getCountry(), e.getDepartment(),
                    e.getJobTitle(), e.getAnnualSalary(), e.getCurrency(), e.getHireDate(), e.getStatus());
        }
    }

    /** What the create and edit forms send. The rules here are checked before any of our code runs. */
    record EmployeeRequest(
            @NotBlank @Size(max = 120) String fullName,
            @NotBlank @Email @Size(max = 200) String email,
            @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be a 2-letter country code such as IN") String country,
            @NotBlank @Size(max = 80) String department,
            @NotBlank @Size(max = 120) String jobTitle,
            @NotNull @Positive(message = "must be more than 0") Long annualSalary,
            @NotBlank @Pattern(regexp = "[A-Z]{3}", message = "must be a 3-letter currency code such as INR") String currency,
            @NotNull @PastOrPresent(message = "cannot be in the future") LocalDate hireDate,
            @NotBlank @Pattern(regexp = "ACTIVE|ON_LEAVE|TERMINATED", message = "must be ACTIVE, ON_LEAVE or TERMINATED") String status) {

        EmployeeData toData() {
            return new EmployeeData(fullName, email, country, department, jobTitle, annualSalary, currency, hireDate, status);
        }
    }

    /** One page of results plus what the UI needs to draw the pager. {@code page} starts at 0. */
    record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    }

    private final EmployeeService employeeService;
    private final AuthContext authContext;

    EmployeeController(EmployeeService employeeService, AuthContext authContext) {
        this.employeeService = employeeService;
        this.authContext = authContext;
    }

    /** Example: GET /api/employees?page=0&size=25&sort=annualSalary,desc&search=maya&country=IN&status=ACTIVE */
    @GetMapping
    @CheckAbility(action = Action.READ, subject = Subject.EMPLOYEE)   // policy guard: must be allowed to read employees
    PageResponse<EmployeeView> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size,
            @RequestParam(defaultValue = "id,desc") String sort,   // newest first: the highest id is the most recently added
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String country,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status) {

        if (status != null && !status.isBlank() && !STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be one of " + STATUSES);
        }
        PageRequest pageRequest = PageRequest.of(
                Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE), parseSort(sort));

        Page<Employee> result = employeeService.find(
                new Filters(search, country, department, status), pageRequest, authContext.currentAbility());

        return new PageResponse<>(result.map(EmployeeView::of).getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    @CheckAbility(action = Action.READ, subject = Subject.EMPLOYEE)
    EmployeeView get(@PathVariable long id) {
        return EmployeeView.of(employeeService.get(id, authContext.currentAbility()));
    }

    @PostMapping
    @CheckAbility(action = Action.CREATE, subject = Subject.EMPLOYEE)
    ResponseEntity<EmployeeView> create(@Valid @RequestBody EmployeeRequest request) {
        Employee created = employeeService.create(request.toData(), authContext.currentAbility());
        return ResponseEntity.created(URI.create("/api/employees/" + created.getId())).body(EmployeeView.of(created));
    }

    @PutMapping("/{id}")
    @CheckAbility(action = Action.UPDATE, subject = Subject.EMPLOYEE)
    EmployeeView update(@PathVariable long id, @Valid @RequestBody EmployeeRequest request) {
        return EmployeeView.of(employeeService.update(id, request.toData(), authContext.currentAbility()));
    }

    @DeleteMapping("/{id}")
    @CheckAbility(action = Action.DELETE, subject = Subject.EMPLOYEE)
    ResponseEntity<Void> delete(@PathVariable long id) {
        employeeService.delete(id, authContext.currentAbility());
        return ResponseEntity.noContent().build();
    }

    /** "annualSalary,desc" becomes a Sort. The id is added last so rows with equal values keep a stable order across pages. */
    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sort by '" + field + "'. Allowed: " + SORTABLE);
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Sort requested = Sort.by(direction, field);
        // Sorting by id already gives a unique order; for any other column add id as the tie-breaker.
        return "id".equals(field) ? requested : requested.and(Sort.by("id"));
    }
}
