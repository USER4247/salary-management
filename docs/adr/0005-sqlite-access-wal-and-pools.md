# ADR-0005: How the app uses SQLite (WAL, one writer, many readers)

- Status: Accepted. Builds on ADR-0003.

## Context
SQLite lets many connections read at once but only one write at a time. With the default journal a write blocks
every reader. HR users mostly read (lists, insights); writes are occasional but must never fail with
"database is locked".

## Decision
- **WAL mode** (write-ahead log), stored in the database file. Readers keep seeing the last committed data while a
  write is in progress, so reads never wait for writes.
- **Two connection pools** behind one routing `DataSource`:
  - writer: exactly 1 connection, transactions start as `BEGIN IMMEDIATE`, so a write takes the lock up front and cannot
    fail halfway while upgrading from read to write; concurrent writes simply queue;
  - readers: 4 read-only connections (the database itself refuses writes on them).
- Spring chooses the pool from `@Transactional(readOnly = true)`. Code that only reads must say so, otherwise it queues
  with the writes. Spring Data's finder methods are already read-only.
- Per-connection settings: `busy_timeout` 5 s (a second process, such as the seed script, makes us wait instead of
  fail), `synchronous=NORMAL` (safe in WAL), `foreign_keys=ON` (SQLite ignores them by default).
- Salaries are stored as whole-unit integers (no floating point money).

## Consequences
- Many concurrent reads, serialized writes: right for this workload. Throughput of writes is limited to one at a time.
- Several app instances sharing one file would work on one machine only; real horizontal scale means PostgreSQL
  (see ADR-0003), where this routing idea stays the same (primary for writes, replicas for reads).
- Tests with a real file prove: WAL is on, readers cannot write, readers are not blocked by an open write, and many
  writers (even from two pools, like two processes) queue without lock errors.
