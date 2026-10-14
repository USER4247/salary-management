# API reference

Every example below is a real response from the running application (demo data, `manager@acme.test` unless stated).
Source code: `backend/src/main/java/com/acme/salary` (`auth`, `employee`, `insights`).

## Conventions

| | |
|---|---|
| Base path | `/api` (same address as the web app; the browser sends `/api/...` to the backend) |
| Format | JSON in and out, UTF-8, dates as `yyyy-MM-dd`, moments as ISO-8601 UTC (`2026-10-14T15:11:02Z`) |
| Sign-in | HttpOnly cookies `access_token` (15 min) and `refresh_token` (7 days, only sent to `/api/auth`). Tokens are never in a response body. |
| CSRF | Every `POST`, `PUT` and `DELETE` except `POST /api/auth/login` must carry the header `X-XSRF-TOKEN` with the value of the `XSRF-TOKEN` cookie (any earlier response sets it; Angular does this automatically). Without it: `403`. |
| Paging | `page` (from 0) and `size` (default 25, **maximum 100**) |
| Rate limit | Per IP: 300 requests/minute on `/api`, and 10 login attempts/minute. Over the limit: `429` with a `Retry-After` header (seconds). |
| Money | `annualSalary` is a whole number of units of `currency` per year (no decimals) |

### Who may call what

The server decides from the signed-in user's role (see the table in the [README](../README.md)). A regional officer
(`COUNTRY_HR`) only ever sees and changes employees of their own country.

| Endpoint | SUPERADMIN | HR_MANAGER | HR_VIEWER | COUNTRY_HR |
|---|---|---|---|---|
| `GET /api/employees`, `GET /api/employees/{id}` | all | all | all | own country |
| `POST /api/employees` | yes | yes | no | own country only |
| `PUT /api/employees/{id}` | yes | yes | no | own country only (cannot move someone out) |
| `DELETE /api/employees/{id}` | yes | yes | no | no |
| `GET /api/insights/pay` | all | all | all | own country |
| `/api/auth/*` | everyone (own session) | | | |

Not signed in: `401`. Signed in but not allowed: `403`. An employee outside a regional officer's country is reported as
`404` (the same as one that does not exist), so a regional officer cannot learn who works elsewhere.

### Error shapes

Two shapes exist today.

**1. Field problems on a form** (invalid fields, duplicate email, unsupported currency): `application/problem+json` with an
`errors` object mapping field name to message. The Angular forms show each message under its field.

```json
{
  "detail": "Please correct the highlighted fields",
  "instance": "/api/employees",
  "status": 400,
  "title": "Bad Request",
  "errors": {
    "hireDate": "cannot be in the future",
    "fullName": "must not be blank",
    "email": "must be a well-formed email address",
    "annualSalary": "must be more than 0",
    "country": "must be a 2-letter country code such as IN"
  }
}
```

**2. Everything else** (401, 403, 404, a bad sort column or grouping, wrong password) uses Spring Boot's plain shape, without
a message, on purpose for sign-in failures:

```json
{ "timestamp": "2026-10-14T14:56:03.069Z", "status": 401, "error": "Unauthorized", "path": "/api/auth/login" }
```

**Rate limit** (`429`) has its own small body:

```json
{ "title": "Too Many Requests", "status": 429, "detail": "Slow down and retry in 48 seconds" }
```

## Shared shapes (DTOs)

| Shape | Fields |
|---|---|
| `UserView` | `id` number, `email`, `fullName`, `role` (`SUPERADMIN` \| `HR_MANAGER` \| `HR_VIEWER` \| `COUNTRY_HR`), `countryScope` (string or `null`) |
| `Rule` (CASL) | `action` (`manage` \| `read` \| `create` \| `update` \| `delete`), `subject` (`all` \| `Employee` \| `Insights`), optional `conditions` (for example `{"country": "IN"}`). `manage` means any action, `all` any subject. |
| `SessionResponse` | `user` (`UserView`), `accessExpiresAt` (ISO time), `rules` (`Rule[]`) |
| `EmployeeView` | `id`, `fullName`, `email`, `country`, `department`, `jobTitle`, `annualSalary`, `currency`, `hireDate`, `status` |
| `EmployeeRequest` | the same fields without `id`, with the rules in the next table |
| `PageResponse<T>` | `content` (`T[]`), `page`, `size`, `totalElements`, `totalPages` |
| `Stats` | `key`, `headcount`, `total`, `average`, `median`, `min`, `max` (whole US dollars per year) |
| `PayInsights` | `groupBy`, `currency` (always `USD`), `assumption` (text about the exchange rates), `overall` (`Stats`), `groups` (`Stats[]`) |

`EmployeeRequest` rules (checked before any other code runs, then again where the database is needed):

| Field | Rule |
|---|---|
| `fullName` | required, up to 120 characters |
| `email` | required, a valid email, up to 200 characters, **not used by another employee** (case-insensitive) → `409` |
| `country` | required, two capital letters (`IN`) |
| `department` | required, up to 80 characters |
| `jobTitle` | required, up to 120 characters |
| `annualSalary` | required, more than 0 |
| `currency` | required, three capital letters **and one the app has an exchange rate for** (USD, INR, GBP, EUR, CAD, AUD, JPY, SGD, BRL) |
| `hireDate` | required, `yyyy-MM-dd`, not in the future |
| `status` | required: `ACTIVE`, `ON_LEAVE` or `TERMINATED` |

## Sign-in

### `POST /api/auth/login`
No CSRF header needed. Request:

```json
{ "email": "manager@acme.test", "password": "Demo@12345" }
```

`200`. Sets both cookies:

```
Set-Cookie: access_token=<jwt>; Path=/; Max-Age=899; HttpOnly; SameSite=Strict
Set-Cookie: refresh_token=<random>; Path=/api/auth; Max-Age=604799; HttpOnly; SameSite=Strict
```

```json
{
  "user": { "id": 2, "email": "manager@acme.test", "fullName": "Maya Manager", "role": "HR_MANAGER", "countryScope": null },
  "accessExpiresAt": "2026-10-14T15:11:02Z",
  "rules": [
    { "subject": "Employee", "action": "manage" },
    { "subject": "Insights", "action": "read" }
  ]
}
```

| Status | When |
|---|---|
| `400` | email or password blank (`errors`: `"must not be blank"`) |
| `401` | wrong email or password, unknown user and disabled user all give the **same** response |
| `429` | more than 10 attempts per minute from one IP |

The access token carries `sub` (user id), `email`, `role` and, for regional officers, `countryScope`. It carries identity,
not permissions: the server works out what the user may do from the role, and `rules` is for the UI to show or hide things.

### `GET /api/auth/me`
Who am I? Does not change any token. Returns a `SessionResponse` (same body as login) or `401`.

### `POST /api/auth/refresh`
Needs the CSRF header and the `refresh_token` cookie (the access cookie may be expired or missing). Swaps the refresh token
for a new pair; **each refresh token works only once**. `200` with new cookies and a `SessionResponse`, or `401` if the
token is unknown, used, expired, or its user is disabled.

### `POST /api/auth/logout`
Needs the CSRF header. Deletes the refresh token on the server and expires both cookies. `204`, always (also if nobody was
signed in).

```
Set-Cookie: access_token=; Path=/; Max-Age=0; HttpOnly; SameSite=Strict
Set-Cookie: refresh_token=; Path=/api/auth; Max-Age=0; HttpOnly; SameSite=Strict
```

## Employees

### `GET /api/employees`
A page of employees.

| Parameter | Meaning |
|---|---|
| `page`, `size` | paging (see conventions) |
| `sort` | `field,direction`, for example `annualSalary,desc`. Fields: `id`, `fullName`, `email`, `country`, `department`, `jobTitle`, `annualSalary`, `hireDate`, `status`. **Default `id,desc`: newest first** (the highest `id` is the most recently added employee). Anything else → `400`. |
| `search` | text found in name, email or job title (not case sensitive; `%` and `_` are plain characters) |
| `country`, `department`, `status` | exact filters (`status` must be one of the three values, else `400`) |

Filters only narrow the result: a regional officer asking for another country gets an empty page, not an error.
Example: `GET /api/employees?size=2&sort=annualSalary,desc&country=IN&status=ACTIVE`

```json
{
  "content": [
    {
      "id": 8524, "fullName": "Isha Sharma", "email": "isha.sharma8524@acme.test", "country": "IN",
      "department": "Engineering", "jobTitle": "Staff Engineer", "annualSalary": 8900000, "currency": "INR",
      "hireDate": "2014-11-10", "status": "ACTIVE"
    },
    {
      "id": 3285, "fullName": "Isha Nair", "email": "isha.nair3285@acme.test", "country": "IN",
      "department": "Engineering", "jobTitle": "Staff Engineer", "annualSalary": 8370000, "currency": "INR",
      "hireDate": "2017-03-02", "status": "ACTIVE"
    }
  ],
  "page": 0,
  "size": 2,
  "totalElements": 1883,
  "totalPages": 942
}
```

### `GET /api/employees/{id}`
One `EmployeeView`, or `404` (no such employee, or outside your region).

### `POST /api/employees`
Body: an `EmployeeRequest`. Example:

```json
{
  "fullName": "Priya Nair", "email": "priya.nair@acme.test", "country": "IN", "department": "Finance",
  "jobTitle": "Financial Analyst", "annualSalary": 1800000, "currency": "INR", "hireDate": "2024-03-01", "status": "ACTIVE"
}
```

`201` with `Location: /api/employees/10001` and the created `EmployeeView` (with its `id`).

| Status | When |
|---|---|
| `400` | a field breaks its rule: see the `errors` object (example under "Error shapes"), or `{"errors": {"currency": "Unsupported currency XYZ"}}` |
| `403` | missing CSRF header, role may not create, or a regional officer adding to another country |
| `409` | email already used: `{"detail": "Another employee already uses this email", "errors": {"email": "Another employee already uses this email"}, "status": 409, "title": "Conflict"}` |

### `PUT /api/employees/{id}`
Replaces every editable field with the `EmployeeRequest` (send the whole employee). `200` with the updated `EmployeeView`.
An employee may keep their own email. `400` and `409` as for create; `403` if the role may not edit, or a regional officer tries
to move someone to another country; `404` if not found or outside the region.

### `DELETE /api/employees/{id}`
`204` with no body. `403` for roles that may not delete (regional officers and viewers); `404` if not found or outside the region.
Deleting is permanent.

## Insights

### `GET /api/insights/pay`

| Parameter | Meaning |
|---|---|
| `groupBy` | `country` (default), `department` or `job-title`. Anything else → `400`. |
| `status` | `ACTIVE` (default), `ON_LEAVE`, `TERMINATED` or `ALL` |

All amounts are in **US dollars per year**, converted at fixed rates (the response says which). `median` is the middle salary, or the
average of the two middle ones for an even number of people. `groups` are sorted by `key`. A regional officer gets figures for their
own country only; if there are no matching employees the figures are zero, not an error.
Example: `GET /api/insights/pay?groupBy=department` (two of the eight groups shown):

```json
{
  "groupBy": "DEPARTMENT",
  "currency": "USD",
  "assumption": "Converted to US dollars at fixed rates: 1 USD = 1.52 AUD, 5.0 BRL, 1.36 CAD, 0.92 EUR, 0.79 GBP, 83.0 INR, 150.0 JPY, 1.34 SGD.",
  "overall": { "key": "All", "headcount": 9203, "total": 784655300, "average": 85261, "median": 79000, "min": 10843, "max": 363500 },
  "groups": [
    { "key": "Customer Support", "headcount": 899, "total": 45184863, "average": 50261, "median": 51645, "min": 10843, "max": 139500 },
    { "key": "Engineering", "headcount": 3230, "total": 361330822, "average": 111867, "median": 112000, "min": 19639, "max": 363500 }
  ]
}
```

Note: the request uses `job-title`; the response echoes the grouping in capitals (`JOB_TITLE`).

## Health

`GET /actuator/health` (public) returns `{"groups":["liveness","readiness"],"status":"UP"}`; used by hosting platforms.

## Trying it by hand

Cookies and the CSRF header make plain `curl` slightly fiddly. A sign-in followed by a read and a write:

```bash
# 1. any request sets the XSRF-TOKEN cookie; then sign in (cookies are saved to jar.txt)
curl -s -o /dev/null -c jar.txt -b jar.txt http://localhost:8080/api/auth/me
curl -s -c jar.txt -b jar.txt -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"manager@acme.test","password":"Demo@12345"}'

# 2. read
curl -s -b jar.txt "http://localhost:8080/api/employees?size=2&country=IN"

# 3. write: copy the XSRF-TOKEN value from jar.txt into the header
TOKEN=$(grep XSRF-TOKEN jar.txt | awk '{print $7}')
curl -s -b jar.txt -X DELETE -H "X-XSRF-TOKEN: $TOKEN" http://localhost:8080/api/employees/10001
```
