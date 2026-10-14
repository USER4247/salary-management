# How AI was used

Built with **Claude Code** (Claude Sonnet 5.5) in the Claude desktop app, working in this repository: reading and writing
files, running builds and tests, and making the commits (each carries a `Co-Authored-By` line). The human author set the
direction, chose between options, reviewed what was produced, and redirected it repeatedly. This page is an honest
account of that, not a polished one.

## The loop

1. The author states a goal, or asks a question about options.
2. The AI proposes an approach with tradeoffs; the author decides.
3. The AI implements in small steps and runs the tests; failures are fixed, not hidden.
4. Anything claimed (timings, "it works") is measured against the real application, not assumed.
5. Decisions that matter are written down as ADRs or in `docs/tradeoffs.md`.

## Main instructions given (paraphrased from the session, in order)

1. Read the assessment document; explain the deliverables and any language or technology limits.
2. Work on feature branches merged to `main` by pull request; never commit straight to the main branch.
3. Choose the stack: Java and Spring Boot, Angular, a relational database. The author asked for a plain explanation
   of TDD and Spring since both were new, and asked how Spring compares to Angular and NestJS decorators and guards.
4. "Maven and SQLite": the author wanted nothing extra installed on the workstation, so Postgres and Docker were dropped.
5. Add authentication: JWT with custom claims, CASL-style abilities, guard annotations, cookie sessions with refresh
   tokens, auto-refresh and sign-out in the UI.
6. "It is too much, I want it simpler": the first auth design was rewritten from about 28 files to 15 flat, commented
   ones, with log lines at each step.
7. Add per-IP rate limiting and a `SUPERADMIN` role with absolute access.
8. A single command to run everything (`npm run dev:local`), instead of manual PATH setup.
9. WAL mode, a seed script for 10,000 employees, and a database layer that supports many readers and writers.
10. Employee list limited by region; regional officers see only their country; more demo accounts for them.
11. Insights, then create/edit/delete, "finish all features which are required"; deployment and documentation.

## Where the human changed the AI's direction

- **Complexity:** rejected the first, layered auth design; the simpler version replaced it (history shows both).
- **Developer experience:** rejected a multi-step manual command for running the frontend; the scripts now find the
  right Node themselves.
- **Scope:** the AI argued auth was out of scope for a single-persona brief; the author wanted it as a demonstration of
  system design and it was built, with the requirements document and ADR-0004 updated to say so.
- **Docs in `.gitignore`:** the author first asked to ignore `docs/`; the AI pointed out the brief requires those
  artifacts to be committed, and the author kept them.
- **Required vs nice to have:** the author asked whether audit trails and payslips were needed; the AI said the brief
  does not require them, and they were left out and listed as the next step.

## Where the AI was wrong and the checks caught it

| Problem | How it was found | Fix |
|---|---|---|
| Cookie-authenticated requests skipped CSRF protection (Spring's resource-server setup exempts any token request) | An integration test: a POST with the cookie and no CSRF token returned 200 | Authenticate with an explicit filter ([ADR-0004](../adr/0004-authentication-and-authorization.md)) |
| App could not start outside the tests: SQLite cannot create a missing `./data` folder | Running the real app, not the test suite | Default database file moved into the backend folder |
| CI failed on Linux: `mvnw` was committed without the executable bit | The first CI run on the pull request | Marked executable in Git |
| SQLite "read-only flag" error with pooled connections | Spring integration tests (file-based tests had passed) | Tell the pool the reader connections are read-only |
| Display bug: "$30.0M" instead of "$30M" | A frontend test | Strip the trailing ".0" |
| Misleading test failure that was really a stale build cache | Same test passed alone and failed in the full run | Cleared the cache, reran; noted here so it is not mistaken for a code bug |

## What was and was not verified

- Backend and frontend tests run on every change and in CI. Performance numbers in `docs/performance.md` were taken from
  the running app.
- Not verified: the Docker image build and an actual hosted deployment (Docker is not installed on the development
  machine); behaviour under many simultaneous users; the interface in browsers other than the one used for checking.

## Prompts

The session transcript holds the exact prompts. The list above is a faithful summary of the instructions, not a verbatim
copy; no prompt was altered to look better.
