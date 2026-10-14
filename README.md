# salary-management

Web-based salary management for an organization of 10,000 employees across multiple
countries. Built for HR managers to maintain salary data and answer questions about
how the organization pays people.

## Stack

| Layer | Choice |
|---|---|
| Backend | Java 21, Spring Boot 3 |
| Database | PostgreSQL, Flyway migrations |
| UI | Angular, TypeScript |
| Tests | JUnit 5, AssertJ, Mockito |

## Getting started

_Coming soon._

## Architecture

_Coming soon._ Decisions are recorded in [`docs/adr`](docs/adr).

## Testing

_Coming soon._

## Deployment

_Coming soon._

## Workflow

- Trunk-based: short-lived branches named `type/short-description`, merged to `main` via PR
  using merge commits ([ADR-0001](docs/adr/0001-trunk-based-development.md)).
- Commits follow [Conventional Commits](https://www.conventionalcommits.org/)
  (`feat:`, `fix:`, `test:`, `docs:`, `chore:`, `refactor:`).
- Work is test-first where practical: failing test, implementation, refactor, as separate small commits.
- Secrets are never committed ([ADR-0002](docs/adr/0002-configuration-and-secrets.md)).
