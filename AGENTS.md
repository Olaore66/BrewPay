# BrewPay agent rules

This file is the fence for every AI session. Read it before editing.

## This slice

Slice 5 is the idempotency store for posting a journal. The same key, organization, and action returns the first journal. A different request that reuses the key is rejected and stores nothing new. Slices 1 to 4 still hold. Do not add webhooks, a fake bank, transfers, reconciliation, or a Kolomoni client in this slice.

## Rules that already have tests

- Every HTTP response carries `X-Correlation-Id`. A missing or unsafe header is replaced. The id is also on the error body.
- Every error body is `code`, `message`, `correlationId`, `details`. A 500 message is generic. The exception text stays in the log.
- `ledger`, `payments`, `identity`, `access`, `audit`, `reconciliation`, and `notifications` do not depend on `com.brewpay.banking.kolomoni`. The port package `com.brewpay.banking` does not depend on the adapter either.
- A user read or write is scoped by the organization id on the authenticated session. Organization A asking for organization B's user gets the same not-found as an unknown user. `UserRepository` has no finder that omits the organization id. Email is unique per organization, stored lowercase.
- `X-Organization-Id` does not choose the tenant. A missing or unknown session is `401`. A revoked session and an expired session are both `Session is invalid.`
- Wrong password, unknown email, and unknown organization share the message `Email or password is wrong.`
- The owner can add a user and read `/api/v1/audit-events`. Staff can read users in their own organization. Staff cannot add a user or read the audit.
- Passwords are bcrypt hashes. Session secrets are random; the database stores SHA-256 hex. Audit rows do not contain the password or the session secret. `AuditRepository` has no delete method.
- Money is an integer number of kobo. The server stores currency `NGN`. A journal is stored only when the debit total equals the credit total. An unbalanced journal returns `Journal is not balanced.` A total that overflows a Java long returns `Journal amount is too large.` A reversal is a new journal with the directions swapped. The original journal is not updated. A second reversal of the same journal returns `Journal is already reversed.`
- Ledger reads and writes use the organization id on the session. Another organization's journal or account is the same not-found as an unknown one. Only the owner can use the ledger routes. `LedgerAccountRepository`, `JournalRepository`, and `JournalLineRepository` have no finder that omits the organization id and no delete method. The ledger package does not import `identity` or `payments`.
- `POST /api/v1/ledger/journals` requires `Idempotency-Key`. The same key, organization, and action returns the first journal and does not post a second one. The same key with a different request returns `Idempotency key was already used with a different request.` A rejected post does not consume the key. Another organization's copy of the key is a separate request. `IdempotencyRepository` has no finder that omits the organization id and no delete method.

## How to change the code

- One rule per change. Add or update the test first.
- Spotless is part of `./mvnw verify`. Format with `./mvnw spotless:apply`.
- Do not edit a merged Flyway migration. `V1__organization_and_user.sql`, `V2__session_and_audit.sql`, `V3__ledger.sql`, and `V4__idempotency.sql` stay as they are. Add `V5__...` for the next change.
- Provider payloads stay in `banking.kolomoni` when that adapter exists. Domain code calls the port.
- Do not log secrets, passwords, session tokens, BVNs, or raw provider payloads.
- Child reads go through `UserRepository` methods that take `organizationId`. Do not add `findById`, `findAll`, or extend `CrudRepository` / `JpaRepository` on that interface.
- Do not extend `CrudRepository` on `SessionRepository`, `AuditRepository`, `LedgerAccountRepository`, `JournalRepository`, `JournalLineRepository`, or `IdempotencyRepository`.

## Session

`POST /api/v1/organizations` is public and creates the owner with a password. `POST /api/v1/sessions` is public. The body names the organization, because the same email may exist in two organizations. Every other `/api/v1` route needs `Authorization: Bearer`. The session lives for `brewpay.session.ttl` (12 hours) unless `DELETE /api/v1/sessions/current` revokes it. Roles in this slice are `OWNER` and `STAFF` only.

## Ledger

The owner creates ledger accounts, then posts a journal. Amounts are `amountKobo`, a positive whole number of kobo. The client does not send a currency. Each line is `DEBIT` or `CREDIT`. At least two lines are required, and the debit total must equal the credit total. `POST /api/v1/ledger/journals` also sends `Idempotency-Key`: 8 to 128 characters from `A-Z`, `a-z`, `0-9`, `.`, `_`, `:`, and `-`. `POST /api/v1/ledger/journals/{id}/reversals` appends a new journal that points at the original. There is no update and no delete. Account codes are unique per organization and stored in uppercase. A line cannot attach an account from another organization: the database foreign key includes the organization id, and the service lookup does too.

## Idempotency

The key is stored with the organization and the action `JOURNAL_POST` only after the journal commits. A second call with the same key and the same description and lines returns that journal, including the same id. A second call with the same key and a different amount or account returns `409` and leaves the first journal in place. An unbalanced journal, a missing account, and a staff denial do not consume the key. The stored row has no delete method. The response and the audit do not echo the key.

## Local database

The application connects to `jdbc:postgresql://localhost:5432/brewpay_db` unless `BREWPAY_DB_URL` is set. The user is `BREWPAY_DB_USER` (default `brewpay`) and the password is `BREWPAY_DB_PASSWORD` (default empty). This repo does not create `brewpay_db`. `./mvnw verify` does not need it: the tests start PostgreSQL 16 in Testcontainers. A database that already applied `V1` and has user rows must be recreated before `V2`, because `V2` adds required password and role columns. `V3` adds the ledger tables on top of `V2`. `V4` adds the idempotency table on top of `V3`.
