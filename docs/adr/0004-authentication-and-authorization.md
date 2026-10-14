# ADR-0004: Authentication and authorization

- Status: Accepted (supersedes the "auth out of scope" note in the first requirements draft)

## Context
The brief names a single persona, so auth is not required. It is added deliberately to show
system design: separation of authentication (who are you) from authorization (what may you do),
and a permission model that can grow without rewriting controllers.

## Decision

**Session**
- Access token: short-lived (15 min) signed JWT with custom claims `role` and `countryScope`.
  Sent as an `HttpOnly`, `SameSite=Strict` cookie, so scripts cannot read it.
- Refresh token: opaque random value (not a JWT), 7 days, in an `HttpOnly` cookie scoped to
  `/api/auth`. Only its SHA-256 hash is stored. It is **rotated on every use**; presenting an
  already-used token revokes the whole token family (theft detection).
- Signing: HS256 with `JWT_SECRET` from the environment (ADR-0002). RS256 is the production
  option but needs key management that is out of proportion here.
- CSRF: cookies make CSRF possible, so `SameSite=Strict` is combined with Spring's cookie CSRF
  token, which Angular's `HttpClient` already echoes in `X-XSRF-TOKEN`. Only `POST /api/auth/login`
  is exempt (it requires credentials and has no session to hijack).
- UI and API are served from the same origin (dev proxy locally; Spring serves the built UI when deployed).

**Authorization, in three layers** (mirroring guard-style architectures)
1. *Authentication guard*: Spring Security resource-server filter validating the cookie JWT.
2. *Access guard*: `@RequireRole(...)` for coarse role checks.
3. *Policy guard*: `@CheckAbility(action, subject)` for fine-grained checks.
   Abilities follow the CASL model (`action`, `subject`, optional `conditions`), built by an
   `AbilityFactory` from the principal. Handlers may further check a concrete resource
   (for example the employee's country) against the ability.

**Tokens carry identity, not permissions.** The JWT holds role and scope; abilities are derived
on the server, so changing a role's permissions does not leave stale tokens in circulation.
`GET /api/auth/me` returns the ability rules in CASL's raw format for the UI to display.
The server always enforces; the UI ability only hides controls.

**Client**: sign-in screen, route guard, interceptor that on `401` performs a single shared
refresh and retries, a proactive refresh timer driven by the `expiresAt` returned in response
bodies (the HttpOnly token cannot be read), sign-out that revokes server-side and clears
state, and cross-tab sign-out through `BroadcastChannel`.

## Consequences
- Tokens are never reachable from JavaScript, limiting XSS impact.
- Same-origin hosting is required; deployment must keep the UI and API on one host.
- Demo credentials are shown on the sign-in screen for the deployed assessment build only.
- Not included: password reset, MFA, SSO, per-device session management.
