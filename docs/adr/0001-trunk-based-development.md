# ADR-0001: Trunk-based development

- Status: Accepted

## Context
Single developer, single deployable application, short delivery window. The commit and PR
history is itself a deliverable and should show how the solution evolved.

## Decision
- `main` is always deployable.
- All work happens on short-lived branches named `type/short-description`
  (`feature/`, `fix/`, `chore/`, `docs/`) and is merged into `main` through a pull request.
- PRs are merged with a merge commit (no squash) so that incremental commits stay visible.
- No long-lived qa/sandbox/prod branches. Environment differences are handled with
  Spring profiles and environment variables (see ADR-0002).
- Commits follow Conventional Commits.

## Consequences
- Low merge overhead and a readable history.
- A release is a tag on `main`.
- If multiple deploy targets or release trains appear later, this decision should be revisited.
