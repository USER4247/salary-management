package com.acme.salary.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/** JpaSpecificationExecutor lets us build the search and filter conditions in code (see EmployeeService). */
public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    boolean existsByEmailIgnoreCase(String email);

    /** Used when editing: the employee may keep their own email, but not take someone else's. */
    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
