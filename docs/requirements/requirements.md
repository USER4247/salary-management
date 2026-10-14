# Salary Management: Requirements

## Goal
Replace the HR team's Excel files with a web application where an HR Manager can maintain
salary data for ~10,000 employees across multiple countries and quickly answer
"how do we pay people?" questions.

## User
**HR Manager** (single persona). Non-technical, works daily with employee records, and
needs answers without building pivot tables.

## Scope (v1)

**Manage employees**
- Create, view, edit and delete employees: name, email, country, department, job title,
  salary, currency, hire date, employment status.
- Employee list with server-side search, filtering (country, department, job title, status),
  sorting and pagination, so it stays fast at 10,000+ rows.
- Validation: required fields, positive salary, valid email, supported country/currency.

**Pay insights** (the "answer questions" part)
- Headcount and salary stats (min, max, average, median) by country, department and job title.
- Salary distribution (bands) for a selected slice.
- Pay range per job title across countries.
- Top and bottom earners, and simple outlier flagging (salary far from the job-title median).
- Org-wide payroll total, normalised to one reporting currency.

**Platform**
- Seed script generating 10,000 realistic employees.
- Deployed, live application, with a demo video.

## Deliberately left out (and why)
| Not in v1 | Reasoning |
|---|---|
| Authentication and roles | One persona is specified. Adding it would cost time better spent on the core. Planned as a follow-up (JWT with role claims). |
| Excel/CSV import and export | The seed script covers initial data. Import needs validation and error-reporting UX that deserves its own iteration. Natural next step. |
| Live FX rates | Currency conversion uses a fixed, documented rate table. Real rates add an external dependency and make results non-deterministic. |
| Payroll processing, tax, payslips | The problem is managing and understanding salary data, not running payroll. |
| Salary history / audit trail | Valuable, but doubles the data model. Flagged as a future ADR. |
| Multi-tenant, i18n, mobile app | Single organisation, English, responsive web is enough. |

## Non-functional requirements
- List and insight queries respond in under 500 ms at 10,000 employees (aggregation in SQL, indexed).
- Seeding 10,000 employees completes in seconds (batched inserts).
- Fast, deterministic unit tests; the core business rules are tested without a database.
- Reproducible: one command to run locally (no external services; embedded SQLite), configuration via environment variables.

## Key assumptions
- One salary per employee (current annual gross), stored in local currency.
- Reporting currency is USD, using fixed rates.
- Country and currency lists are a small fixed set (about 10 countries).

## Open questions for Incubyte
1. Is "AngularJS for Java" meant as modern Angular (v17+)?
2. Is cross-country comparison via a fixed rate table acceptable, or should local currency only be shown?
3. Are authentication and roles expected, or optional?
