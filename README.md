# BrewPay

Merchant financial operations platform. Slice 2 adds an organization, its owner, and users. One organization cannot read another organization's user. Slice 1 still provides the build, CI, formatting, package boundaries, a correlation id, and one error shape.

## Run the checks

```
./mvnw verify
```

CI runs that command on every push and pull request. The organization tests start PostgreSQL 16 with Testcontainers. They do not use the local `brewpay_db` database.

## Run the service

```
./mvnw spring-boot:run
```

The process opens PostgreSQL at startup. The default URL is `jdbc:postgresql://localhost:5432/brewpay_db`. Set `BREWPAY_DB_USER` and `BREWPAY_DB_PASSWORD` when the local role is not `brewpay` with an empty password. This repo does not create the database.

`GET /api/v1/health` returns `{"status":"UP"}` and sets `X-Correlation-Id`.

## Organizations and users

`POST /api/v1/organizations` creates an organization and its owner. No organization header.

```json
{
  "name": "Ada Stores",
  "owner": { "firstName": "Ada", "lastName": "Okeke", "email": "ada@example.com" }
}
```

User routes require `X-Organization-Id` (a UUID). That header is a stand-in until login binds the organization. It is not authentication.

- `POST /api/v1/users` adds a user to that organization.
- `GET /api/v1/users` lists that organization's users, oldest first.
- `GET /api/v1/users/{id}` returns the user when the id belongs to that organization.

A user id from another organization and an unknown user id both return `404` with message `User was not found.` Email is unique inside one organization and is stored in lowercase. The same email may exist in a second organization. There is no password on the user yet.

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

Send `X-Correlation-Id` to keep your own id. The value must be 8 to 64 characters from `A-Z`, `a-z`, `0-9`, `.`, `_`, and `-`. Anything else is replaced.

## Packages

| Package | Owns |
|---|---|
| `platform` | Correlation id, error shape, tenant context, health |
| `identity` | Organizations, staff, sessions |
| `access` | Roles and permissions |
| `audit` | Append-only audit |
| `ledger` | Journals. Source of truth for money |
| `payments` | Collections, transfers, idempotency |
| `banking` | Provider port |
| `banking.kolomoni` | Kolomoni adapter only |
| `reconciliation` | Mismatch detection |
| `notifications` | Email and SMS |

`ledger` and `payments` must not import `banking.kolomoni`. `PackageRulesTest` fails the build if they do.
