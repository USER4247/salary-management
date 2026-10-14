# ADR-0003: SQLite as the database

- Status: Accepted

## Context
The assessment allows any relational database ("like SQLite"). The workload is a single
HR persona on ~10,000 rows. The project should run with no external services and keep
tests fast and deterministic.

## Decision
Use SQLite (`sqlite-jdbc` + Hibernate community SQLite dialect) with Flyway migrations.
- Tests use an in-memory/temporary database; no containers needed.
- SQLite has no `percentile_cont`, so medians use window functions (`ROW_NUMBER`/`COUNT`
  per group) or a small in-service calculation.
- Hosting free tiers have ephemeral disks: the database is seeded on startup when empty
  (10,000 rows take seconds with batched inserts).
- SQL stays standard and data access goes through JPA to keep a Postgres migration cheap.

## Consequences
- Zero-setup local run and CI.
- Single writer at a time: acceptable for one persona, a known limit for multi-user scale.
- Production-scale path: PostgreSQL via dialect/config change plus a persistent database.
