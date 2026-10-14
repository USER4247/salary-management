# salary-management

Web-based salary management for an organization of 10,000 employees across multiple
countries. Built for HR managers to maintain salary data and answer questions about
how the organization pays people.

## Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3 |
| Database | SQLite (see [ADR-0003](docs/adr/0003-sqlite-database.md)), Flyway migrations |
| UI | Angular 22, TypeScript, SCSS |
| Tests | JUnit 5, AssertJ, Mockito |

## Getting started

Prerequisites: JDK 21+ and Node 22.22.3+ (no database server or Docker needed; SQLite is embedded).

```bash
# backend: http://localhost:8080 (health: /actuator/health)
cd backend && ./mvnw spring-boot:run

# frontend: http://localhost:4200 (proxies /api to the backend)
cd frontend && npm ci && npm start
```

Tests:

```bash
cd backend && ./mvnw verify
cd frontend && npm test -- --watch=false
```

Configuration is read from environment variables (see [`.env.example`](.env.example) and
[ADR-0002](docs/adr/0002-configuration-and-secrets.md)).

## Architecture

_Coming soon._ Decisions are recorded in [`docs/adr`](docs/adr).

## Repository layout

- `backend/` Spring Boot service
- `frontend/` Angular app
- `docs/` requirements, ADRs and AI-workflow notes

## Deployment

_Coming soon._

## Workflow

- Trunk-based: short-lived branches named `type/short-description`, merged to `main` via PR
  using merge commits ([ADR-0001](docs/adr/0001-trunk-based-development.md)).
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `test:`, `docs:`, `chore:`, `refactor:`).
- Work is test-first where practical: failing test, implementation, refactor, as separate small commits.
- Secrets are never committed ([ADR-0002](docs/adr/0002-configuration-and-secrets.md)).
