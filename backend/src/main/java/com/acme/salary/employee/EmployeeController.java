package com.acme.salary.employee;

import com.acme.salary.auth.Ability.Action;
import com.acme.salary.auth.Ability.Subject;
import com.acme.salary.auth.AuthContext;
import com.acme.salary.auth.CheckAbility;
import com.acme.salary.employee.EmployeeService.Filters;
import java.util.List;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** The employee list. Data is paged: the browser never receives all 10,000 rows at once. */
@RestController
@RequestMapping("/api/employees")
class EmployeeController {

    private static final int MAX_PAGE_SIZE = 100;
    /** Only these columns may be sorted on. Never pass the sort text straight to the database layer. */
    private static final Set<String> SORTABLE = Set.of(
            "fullName", "email", "country", "department", "jobTitle", "annualSalary", "hireDate", "status");
    private static final Set<String> STATUSES = Set.of("ACTIVE", "ON_LEAVE", "TERMINATED");

    record EmployeeView(Long id, String fullName, String email, String country, String department, String jobTitle,
                        long annualSalary, String currency, String hireDate, String status) {
        static EmployeeView of(Employee e) {
            return new EmployeeView(e.getId(), e.getFullName(), e.getEmail(), e.getCountry(), e.getDepartment(),
                    e.getJobTitle(), e.getAnnualSalary(), e.getCurrency(), e.getHireDate(), e.getStatus());
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
            @RequestParam(defaultValue = "fullName,asc") String sort,
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

    /** "annualSalary,desc" becomes a Sort. The id is added last so rows with equal values keep a stable order across pages. */
    private static Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String field = parts[0].trim();
        if (!SORTABLE.contains(field)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot sort by '" + field + "'. Allowed: " + SORTABLE);
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, field).and(Sort.by("id"));
    }
}
