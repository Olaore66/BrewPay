# BrewPay

Merchant financial operations platform. Slice 3 adds login, sessions, an owner permission check, and an audit of those actions. A session chooses the organization. Slice 2 still keeps one organization from reading another's user.

## Run the checks

```
./mvnw verify
```

CI runs that command on every push and pull request. The tests start PostgreSQL 16 with Testcontainers. They do not use the local `brewpay_db` database.

## Run the service

```
./mvnw spring-boot:run
```

The process opens PostgreSQL at startup. The default URL is `jdbc:postgresql://localhost:5432/brewpay_db`. Set `BREWPAY_DB_USER` and `BREWPAY_DB_PASSWORD` when the local role is not `brewpay` with an empty password. This repo does not create the database.

`GET /api/v1/health` returns `{"status":"UP"}` and sets `X-Correlation-Id`. It does not need a session.

## Organizations and users

`POST /api/v1/organizations` creates an organization and its owner. The owner sets a password of 8 to 72 characters. This route is public.

```json
{
  "name": "Ada Stores",
  "owner": {
    "firstName": "Ada",
    "lastName": "Okeke",
    "email": "ada@example.com",
    "password": "password-1"
  }
}
```

The owner is role `OWNER`. `POST /api/v1/users` adds a `STAFF` user to the session's organization. The owner chooses that user's first password. There is no invite flow yet.

- `GET /api/v1/users` lists that organization's users, oldest first.
- `GET /api/v1/users/{id}` returns the user when the id belongs to that organization.

A user id from another organization and an unknown user id both return `404` with message `User was not found.` Email is unique inside one organization and is stored in lowercase. The same email may exist in a second organization. Responses do not include the password.

## Login and sessions

`POST /api/v1/sessions` is public. The body names the organization, because one email can belong to two organizations.

```json
{ "organizationId": "...", "email": "ada@example.com", "password": "password-1" }
```

The response includes a token once. Send it as `Authorization: Bearer` on later requests. The server stores only a SHA-256 hash of the token. The password is stored as a bcrypt hash. A session lasts 12 hours (`brewpay.session.ttl`) unless `DELETE /api/v1/sessions/current` revokes it. Wrong password, unknown email, and unknown organization all return `401` with `Email or password is wrong.`

`X-Organization-Id` is ignored. The session's organization is the tenant.

Staff can read users in that organization. Only the owner can add a user or call `GET /api/v1/audit-events`. The audit records organization signup, login, rejected login, logout, user creation, and the two denials. The rows are append-only. They do not contain the password or the session token.

## Errors

Every error body has the same four fields:

```json
{
  "code": "NOT_FOUND",
  "message": "User was not found.",
  "correlationId": "2f1c...",
  "details": []
}
```

`UNAUTHORIZED` is 401. `FORBIDDEN` is 403. Send `X-Correlation-Id` to keep your own id. The value must be 8 to 64 characters from `A-Z`, `a-z`, `0-9`, `.`, `_`, and `-`. Anything else is replaced.

## Packages

| Package | Owns |
|---|---|
| `platform` | Correlation id, error shape, tenant context, health |
| `identity` | Organizations, staff, sessions |
| `access` | Roles and the owner check |
| `audit` | Append-only audit |
| `ledger` | Journals. Source of truth for money |
| `payments` | Collections, transfers, idempotency |
| `banking` | Provider port |
| `banking.kolomoni` | Kolomoni adapter only |
| `reconciliation` | Mismatch detection |
| `notifications` | Email and SMS |

`ledger` and `payments` must not import `banking.kolomoni`. `PackageRulesTest` fails the build if they do.
