# Performance

Measured on the real application with the full 10,000-employee dataset (the packaged jar, a fresh SQLite file, a
developer laptop). Times are the server's own request log (`API GET ... in N ms`), three calls each. The first call
after start-up is slower while the JVM and the connection pools warm up.

| Request | 1st call | Next calls |
|---|---|---|
| List, first page of 25 | 69 ms | 10 to 11 ms |
| List sorted by salary, highest first | 10 ms | 10 to 12 ms |
| List with search ("engineer") | 35 ms | 26 to 28 ms |
| List with country + department + status filters | 15 ms | 10 ms |
| List, page 51 of size 100 (deep paging) | 27 ms | 26 to 27 ms |
| Pay insights by job title (all employees, overall + per group) | 74 ms | 62 ms |
| Loading all 10,000 employees on a first start | 121 ms (one transaction) | not repeated |

The target in the requirements document was under 500 ms at 10,000 employees; every request is far inside it.
Start-up of the packaged app takes about 6.5 seconds.

## Why it is fast, and what to watch

- **The browser never receives all rows.** The server pages (maximum 100 per page) and does searching, filtering and
  sorting in the database.
- **Insights are calculated inside the database** with one window-function query per grouping (SQLite has no built-in
  median), so the 10,000 rows never leave it. Currency conversion happens in the same query.
- **Reads and writes do not block each other:** WAL mode, one writer connection, four read-only connections
  ([ADR-0005](adr/0005-sqlite-access-wal-and-pools.md)). Tests with a real file prove readers are not blocked by an open
  write and that many concurrent writers queue without "database is locked" errors.
- **No extra indexes yet, on purpose.** At 10,000 rows every query above is already tens of milliseconds, so indexes
  would add write cost for no measurable gain. The search uses `LIKE '%text%'`, which cannot use an index and scans the
  table; it takes about 30 ms here. At around 1,000,000 rows that stops being acceptable: the next steps would be
  indexes on `country`, `department` and `job_title` (they help filters and grouping), and SQLite FTS5 (full-text
  search) for the search box. Measure first.

## Known limits

- Not load tested with many simultaneous users: only single-request timings were taken.
- SQLite allows one write at a time. For this workload (mostly reads, occasional edits) that is fine; many editors at
  once would call for PostgreSQL ([ADR-0003](adr/0003-sqlite-database.md)).
- Rate limiting is in memory, per server, and resets on restart.
