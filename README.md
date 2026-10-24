# BrewPay

Merchant financial operations platform. Slice 7 records a payout. A provider timeout stays `UNKNOWN` and does not move money. The same key returns that transfer. A session chooses the organization. One organization still cannot read another's user, account, journal, stored key, collection, or transfer.

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

Staff can read users in that organization. Only the owner can add a user, call `GET /api/v1/audit-events`, use the ledger routes, open a collection account, or submit a transfer. The audit records organization signup, login, rejected login, logout, user creation, ledger posts, reversals, collection accounts, collection credits, transfer submissions, and the denials. The rows are append-only. They do not contain the password, the session token, the webhook secret, the idempotency key, or an amount.

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

`POST /api/v1/ledger/accounts` and `POST /api/v1/ledger/journals` return `201`. A journal post sends `Idempotency-Key`. The same key and the same lines return the first journal again. A different body with that key returns `409` with `Idempotency key was already used with a different request.` `GET` lists the session's rows, oldest first. `POST /api/v1/ledger/journals/{id}/reversals` appends a new journal with the directions swapped and sets `reversesJournalId` to the original. The original lines stay as they were. A second reversal of that same journal returns `409` with `Journal is already reversed.` An unbalanced journal returns `400` with `Journal is not balanced.` and stores nothing. The key stays free for a later balanced post.

Account codes are unique inside one organization and are stored in uppercase. The same code may exist in a second organization. A journal or account from another organization returns `404`, the same as an unknown id. `X-Organization-Id` is still ignored. Staff receive `403`.

## Collections

The owner opens a collection account by naming two ledger accounts in that organization. Clearing must be `ASSET`. The merchant account must be `LIABILITY`.

```json
{ "clearingAccountId": "...", "merchantAccountId": "..." }
```

`POST /api/v1/collection-accounts` returns `201` and an `externalReference` from the fake bank. `GET /api/v1/collection-accounts` and `GET /api/v1/collections` list that organization's rows, oldest first.

`POST /api/v1/webhooks/fake-bank` is public. The header `X-Fake-Bank-Signature` is the hex HMAC-SHA256 of the raw body. The secret is `BREWPAY_FAKE_BANK_WEBHOOK_SECRET`. The value in this repo is only for the local fake bank. It is not a Kolomoni credential. A bad signature returns `401` with `Webhook signature is invalid.`

A `SUCCESSFUL` body posts one journal: debit the clearing account, credit the merchant account, currency `NGN`, amount `amountKobo`. The response is `201`. Sending that same event again returns the same collection and does not post a second journal. A different amount with the same event returns `409` with `Idempotency key was already used with a different request.` `PENDING`, `FAILED`, and `UNKNOWN` return `200` and store nothing, so a later success with that event can still credit. A collection id from another organization returns `404` with `Collection was not found.`

## Transfers

The owner submits a payout. Clearing must be `ASSET`. The merchant account must be `LIABILITY`. The destination account number is 10 digits. The bank code is 3 to 6 digits. Amounts are whole kobo. Send `Idempotency-Key`.

```json
{
  "clearingAccountId": "...",
  "merchantAccountId": "...",
  "amountKobo": 250000,
  "destinationAccountNumber": "0123456789",
  "destinationBankCode": "058"
}
```

`POST /api/v1/transfers` returns `201`. When the provider does not answer, `status` is `UNKNOWN`, `providerReference` is null, and `journalId` is null. The ledger is unchanged. Sending that same key and body again returns the same transfer and does not ask the provider again. A different amount with that key returns `409` with `Idempotency key was already used with a different request.`

When the provider confirms success, the response is `SUCCESSFUL` and one journal is posted: debit the merchant liability, credit the clearing asset, currency `NGN`. When the provider confirms failure, the status is `FAILED` and no journal is posted. A validation failure and a staff denial do not consume the key. A transfer id from another organization returns `404` with `Transfer was not found.`

The local fake bank answers `SUCCESSFUL` unless it is scripted to time out or fail. A merchant request cannot choose that outcome.

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
| `payments` | Idempotency, collections, and transfers. Money moves only through the ledger |
| `banking` | Provider port |
| `banking.fake` | Fake bank adapter. Signature checks and fake payloads stay here |
| `banking.kolomoni` | Kolomoni adapter only. Empty in this slice |
| `reconciliation` | Mismatch detection |
| `notifications` | Email and SMS |

`ledger` and `payments` must not import `banking.kolomoni` or `banking.fake`. `PackageRulesTest` fails the build if they do.
