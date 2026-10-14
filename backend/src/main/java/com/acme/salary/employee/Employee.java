package com.acme.salary.employee;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** One employee and their pay. Maps to the "employees" table (see the V3 migration). */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    /** ISO country code, e.g. "IN". Regional officers are limited by this. */
    @Column(nullable = false)
    private String country;

    @Column(nullable = false)
    private String department;

    @Column(name = "job_title", nullable = false)
    private String jobTitle;

    /** Whole units of {@link #currency} per year (no decimals, so no rounding errors). */
    @Column(name = "annual_salary", nullable = false)
    private long annualSalary;

    @Column(nullable = false)
    private String currency;

    /** Kept as ISO text "yyyy-MM-dd": it sorts correctly as text and avoids SQLite date conversions. */
    @Column(name = "hire_date", nullable = false)
    private String hireDate;

    /** ACTIVE, ON_LEAVE or TERMINATED. */
    @Column(nullable = false)
    private String status;

    protected Employee() {
        // required by JPA
    }

    public Employee(String fullName, String email, String country, String department, String jobTitle,
                    long annualSalary, String currency, String hireDate, String status) {
        this.fullName = fullName;
        this.email = email;
        this.country = country;
        this.department = department;
        this.jobTitle = jobTitle;
        this.annualSalary = annualSalary;
        this.currency = currency;
        this.hireDate = hireDate;
        this.status = status;
    }

    /** Replaces every editable field at once (used by the edit form, which always sends the whole employee). */
    public void update(String fullName, String email, String country, String department, String jobTitle,
                       long annualSalary, String currency, String hireDate, String status) {
        this.fullName = fullName;
        this.email = email;
        this.country = country;
        this.department = department;
        this.jobTitle = jobTitle;
        this.annualSalary = annualSalary;
        this.currency = currency;
        this.hireDate = hireDate;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getFullName() { return fullName; }
    public String getEmail() { return email; }
    public String getCountry() { return country; }
    public String getDepartment() { return department; }
    public String getJobTitle() { return jobTitle; }
    public long getAnnualSalary() { return annualSalary; }
    public String getCurrency() { return currency; }
    public String getHireDate() { return hireDate; }
    public String getStatus() { return status; }
}
