# BrewPay

Merchant financial operations platform. Slice 4 stores a balanced journal in kobo and reverses it with a new journal. A session chooses the organization. One organization still cannot read another's user, account, or journal.

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

Staff can read users in that organization. Only the owner can add a user, call `GET /api/v1/audit-events`, or use the ledger routes. The audit records organization signup, login, rejected login, logout, user creation, ledger posts, reversals, and the denials. The rows are append-only. They do not contain the password, the session token, or an amount.

## Ledger

The owner creates an account, then posts a journal. Amounts are whole kobo. The server stores the currency as `NGN`.

```json
{ "code": "cash", "name": "Cash", "type": "ASSET" }
```

```json
{
  "description": "Till sales",
  "lines": [
    { "accountId": "...", "direction": "DEBIT", "amountKobo": 150000 },
    { "accountId": "...", "direction": "CREDIT", "amountKobo": 150000 }
  ]
}
```

`POST /api/v1/ledger/accounts` and `POST /api/v1/ledger/journals` return `201`. `GET` lists the session's rows, oldest first. `POST /api/v1/ledger/journals/{id}/reversals` appends a new journal with the directions swapped and sets `reversesJournalId` to the original. The original lines stay as they were. A second reversal of that same journal returns `409` with `Journal is already reversed.` An unbalanced journal returns `400` with `Journal is not balanced.` and stores nothing.

Account codes are unique inside one organization and are stored in uppercase. The same code may exist in a second organization. A journal or account from another organization returns `404`, the same as an unknown id. `X-Organization-Id` is still ignored. Staff receive `403`.

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
| `ledger` | Accounts and balanced journals in kobo. Source of truth for money |
| `payments` | Collections, transfers, idempotency |
| `banking` | Provider port |
| `banking.kolomoni` | Kolomoni adapter only |
| `reconciliation` | Mismatch detection |
| `notifications` | Email and SMS |

`ledger` and `payments` must not import `banking.kolomoni`. `PackageRulesTest` fails the build if they do.
