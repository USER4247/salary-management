# Demo script (about 5 minutes)

Record the screen with a narrated walkthrough. Start from a fresh deploy (or `npm run dev:local` then
`npm run db:seed`) so the numbers below match. Password for every account: `Demo@12345`.

| Time | Do | Say |
|---|---|---|
| 0:00 | Open the app. The sign-in page appears. | "HR teams manage pay in Excel. This replaces that for 10,000 employees in 10 countries." |
| 0:20 | Click **HR Manager**, sign in. | "Four roles. The manager sees everyone." The header says 10,000 employees. |
| 0:40 | Type "engineer" in search; filter Country = India, Department = Engineering; click the **Annual salary** header. | "Search, filters and sorting run on the server; the browser only holds one page. Salaries are shown in each person's own currency." |
| 1:30 | **Add employee**: submit empty (field messages), then use an email that already exists (message under the email field), then save a valid one. | "Checked in the browser and again on the server. Messages appear under the right field." |
| 2:15 | **Edit** that employee, change the salary; then **Delete** it and confirm. | "Deleting asks first." |
| 2:45 | Open **Insights**. Switch Country / Department / Job title. Point at the exchange-rate note. | "Total, average, median and spread, all in dollars at fixed, documented rates, calculated inside the database in about 60 ms." |
| 3:30 | Sign out. Sign in as **hr.in@acme.test** (country picker: India). | "A regional officer. Same screens, but only India: 2,052 employees, no country filter. Other countries' data is never loaded; the limit is in the database query." |
| 4:00 | Try **Add employee**, note the country is fixed; open **My access**. Sign in as **viewer@acme.test**: no Add/Edit/Delete buttons. | "Buttons follow the role, but the server enforces it: a modified request would be refused." |
| 4:30 | Show the repository: ADRs, tests passing, `docs/performance.md`, the commit history. | "Every feature has tests; decisions and tradeoffs are written down; AI use is documented honestly in `docs/ai`." |

If asked about limits: SQLite (one writer, single instance), fixed exchange rates, no audit trail or payslips yet.
