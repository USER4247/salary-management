# ADR-0004: Authentication and authorization

- Status: Accepted. A first, more layered version was replaced by this simpler one (see "History").

## Context
The brief names one persona, so auth is not required. It is added deliberately to show system design:
separating *who you are* from *what you may do*, with a permission model that can grow. The code must stay
easy to read: a small number of files, plain comments, and log lines at the key steps.

## Decision

**Tokens and cookies**
- Access token: signed JWT (HS256), 15 minutes, with custom claims `email`, `role`, `countryScope`.
- Refresh token: 32 random bytes, 7 days, **one-time use** (used = deleted). Only its SHA-256 hash is stored.
- Both travel only in `HttpOnly`, `SameSite=Strict` cookies (refresh scoped to `/api/auth`), so scripts cannot read
  them. The JSON body carries the user, the access-token expiry and the CASL rules, never a token.
- `JWT_SECRET` (at least 32 characters) comes from the environment (ADR-0002).

**CSRF.** Cookies are sent automatically by browsers, so state-changing requests need a CSRF token: Spring writes an
`XSRF-TOKEN` cookie and Angular's `HttpClient` echoes it in `X-XSRF-TOKEN`. Only `POST /api/auth/login` is exempt.
The JWT is checked by an explicit `AuthenticationFilter`, not `oauth2ResourceServer()`, because that DSL exempts
every token-carrying request from CSRF, which would disable the protection for cookies. An integration test
(a cookie-authenticated POST without a CSRF token returned 200) caught this.

**Permissions (CASL model).** A role maps to a list of rules (`action`, `subject`, optional `conditions`) in one
place, `Ability.forRole`. `SUPERADMIN` gets `manage` on `all` (any action, any subject). Tokens carry identity, not
permissions, so changing a role takes effect immediately. The rules are sent to the UI in CASL's raw format for display;
the server always enforces.

**Guards, in three layers**
1. JWT filter: who is calling.
2. `@RequireRole`: coarse check by role (SUPERADMIN always passes).
3. `@CheckAbility`: fine check by action and subject; handlers may also check a concrete resource through `AuthContext`.

**Rate limiting.** A per-IP fixed-window counter on `/api/**` (300 per minute) and a stricter one on login (10 per
minute), answering `429` with `Retry-After`. It is a first line of defence only: counters are in memory (per instance,
reset on restart), and a real flood needs a CDN or gateway in front. Behind a proxy, the forwarded client address must
be what the server reports as the remote address.

**Client.** Sign-in screen, route guard, interceptor that on `401` refreshes once and retries, a proactive refresh timer
driven by the returned expiry, sign-out that clears server and client state, and cross-tab sign-out.

## History: what was simplified, and the cost
The first version had about 28 files: separate token, session, cookie and DTO classes, abilities split over five files,
and refresh-token *families* that revoked the whole chain when an already-used token reappeared (theft detection).
It was replaced by 15 flat files with comments. The one real security trade-off: a replayed refresh token is now just
rejected instead of also ending the legitimate session. Family revocation is the documented next step.

## Consequences
- UI and API must share one origin (dev proxy locally; Spring serves the built UI when deployed).
- Demo credentials appear on the sign-in screen for the assessment build only.
- Not included: password reset, MFA, SSO, per-device session management, distributed rate limiting.
