# BrewPay

Merchant financial operations platform. Slice 1 is the platform fence: one Spring Boot service, CI, formatting, package boundaries, a correlation id, and one error shape.

## Run the checks

```
./mvnw verify
```

CI runs that command on every push and pull request.

## Run the service

```
./mvnw spring-boot:run
```

`GET /api/v1/health` returns `{"status":"UP"}` and sets `X-Correlation-Id`.

## Errors

Every error body has the same four fields:

```json
{
  "code": "NOT_FOUND",
  "message": "Account was not found.",
  "correlationId": "2f1c...",
  "details": []
}
```

Send `X-Correlation-Id` to keep your own id. The value must be 8 to 64 characters from `A-Z`, `a-z`, `0-9`, `.`, `_`, and `-`. Anything else is replaced.

## Database

Local PostgreSQL, database name `brewpay_db`, on `localhost:5432`. The URL is recorded in `application.yml` as `brewpay.database.url`. This slice does not open a connection and does not create the database. Set `BREWPAY_DB_USER` and `BREWPAY_DB_PASSWORD` when a later slice wires the pool.

## Packages

| Package | Owns |
|---|---|
| `platform` | Correlation id, error shape, health |
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
