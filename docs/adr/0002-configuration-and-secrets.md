# ADR-0002: Configuration and secrets

- Status: Accepted

## Context
The application needs database credentials and, later, possibly a JWT signing key.
These must never be committed, and the same build should run in every environment.

## Decision
- Configuration lives in `application.yml` using `${VAR:default}` placeholders;
  required secrets have no default so startup fails loudly if they are missing.
- Per-environment overrides use Spring profiles (`local`, `prod`).
- Local: gitignored `.env` / `application-local.yml`. CI: GitHub Actions secrets.
  Deployed: the hosting platform's environment variables.
- `.env.example` documents required variables with fake values.
- Nothing secret is ever placed in the Angular bundle.

## Consequences
- One build artifact for all environments.
- A leaked secret must be rotated; deleting it from a later commit is not enough.
