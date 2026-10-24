# BrewPay agent rules

This file is the fence for every AI session. Read it before editing.

## This slice

Slice 6 is a fake bank behind the collection port. The owner opens a collection account on an asset clearing account and a liability merchant account. A signed successful webhook posts one balanced journal in kobo. The same webhook again returns the first collection and does not post a second credit. Slices 1 to 5 still hold. Do not add transfers, reconciliation, or a Kolomoni client in this slice.

## Rules that already have tests

- Every HTTP response carries `X-Correlation-Id`. A missing or unsafe header is replaced. The id is also on the error body.
- Every error body is `code`, `message`, `correlationId`, `details`. A 500 message is generic. The exception text stays in the log.
- `ledger`, `payments`, `identity`, `access`, `audit`, `reconciliation`, `notifications`, and `banking.fake` do not depend on `com.brewpay.banking.kolomoni`. The port package `com.brewpay.banking` does not depend on an adapter. Those domain packages do not depend on `com.brewpay.banking.fake` either.
- A user read or write is scoped by the organization id on the authenticated session. Organization A asking for organization B's user gets the same not-found as an unknown user. `UserRepository` has no finder that omits the organization id. Email is unique per organization, stored lowercase.
- `X-Organization-Id` does not choose the tenant. A missing or unknown session is `401`. A revoked session and an expired session are both `Session is invalid.`
- Wrong password, unknown email, and unknown organization share the message `Email or password is wrong.`
- The owner can add a user and read `/api/v1/audit-events`. Staff can read users in their own organization. Staff cannot add a user or read the audit.
- Passwords are bcrypt hashes. Session secrets are random; the database stores SHA-256 hex. Audit rows do not contain the password or the session secret. `AuditRepository` has no delete method.
- Money is an integer number of kobo. The server stores currency `NGN`. A journal is stored only when the debit total equals the credit total. An unbalanced journal returns `Journal is not balanced.` A total that overflows a Java long returns `Journal amount is too large.` A reversal is a new journal with the directions swapped. The original journal is not updated. A second reversal of the same journal returns `Journal is already reversed.`
- Ledger reads and writes use the organization id on the session. Another organization's journal or account is the same not-found as an unknown one. Only the owner can use the ledger routes. `LedgerAccountRepository`, `JournalRepository`, and `JournalLineRepository` have no finder that omits the organization id and no delete method. The ledger package does not import `identity` or `payments`.
- `POST /api/v1/ledger/journals` requires `Idempotency-Key`. The same key, organization, and action returns the first journal and does not post a second one. The same key with a different request returns `Idempotency key was already used with a different request.` A rejected post does not consume the key. Another organization's copy of the key is a separate request. `IdempotencyRepository` has no finder that omits the organization id and no delete method.
- A successful collection webhook debits the clearing account and credits the merchant account once. The same provider event returns that collection and does not post again. The same event with a different amount returns `Idempotency key was already used with a different request.` A pending, failed, or unknown payment does not post a journal and does not consume the key. A bad signature is rejected. Another organization's collection is the same not-found as an unknown one. `CollectionRepository` has no finder that omits the organization id and no delete method. `findByExternalReference` is the only collection-account lookup that omits the organization id, and only a verified webhook uses it.

## How to change the code

- One rule per change. Add or update the test first.
- Spotless is part of `./mvnw verify`. Format with `./mvnw spotless:apply`.
- Do not edit a merged Flyway migration. `V1__organization_and_user.sql`, `V2__session_and_audit.sql`, `V3__ledger.sql`, `V4__idempotency.sql`, and `V5__collection.sql` stay as they are. Add `V6__...` for the next change.
- Fake bank payloads stay in `banking.fake`. Kolomoni payloads stay in `banking.kolomoni` when that adapter exists. Domain code calls the port.
- Do not log secrets, passwords, session tokens, BVNs, or raw provider payloads.
- Child reads go through `UserRepository` methods that take `organizationId`. Do not add `findById`, `findAll`, or extend `CrudRepository` / `JpaRepository` on that interface.
- Do not extend `CrudRepository` on `SessionRepository`, `AuditRepository`, `LedgerAccountRepository`, `JournalRepository`, `JournalLineRepository`, `IdempotencyRepository`, `CollectionAccountRepository`, or `CollectionRepository`.

## Session

`POST /api/v1/organizations` is public and creates the owner with a password. `POST /api/v1/sessions` is public. The body names the organization, because the same email may exist in two organizations. `POST /api/v1/webhooks/fake-bank` is public. The fake bank checks `X-Fake-Bank-Signature` on the raw body. Every other `/api/v1` route needs `Authorization: Bearer`. The session lives for `brewpay.session.ttl` (12 hours) unless `DELETE /api/v1/sessions/current` revokes it. Roles in this slice are `OWNER` and `STAFF` only.

## Ledger

The owner creates ledger accounts, then posts a journal. Amounts are `amountKobo`, a positive whole number of kobo. The client does not send a currency. Each line is `DEBIT` or `CREDIT`. At least two lines are required, and the debit total must equal the credit total. `POST /api/v1/ledger/journals` also sends `Idempotency-Key`: 8 to 128 characters from `A-Z`, `a-z`, `0-9`, `.`, `_`, `:`, and `-`. `POST /api/v1/ledger/journals/{id}/reversals` appends a new journal that points at the original. There is no update and no delete. Account codes are unique per organization and stored in uppercase. A line cannot attach an account from another organization: the database foreign key includes the organization id, and the service lookup does too.

## Idempotency

The key is stored with the organization and the action `JOURNAL_POST` only after the journal commits. A second call with the same key and the same description and lines returns that journal, including the same id. A second call with the same key and a different amount or account returns `409` and leaves the first journal in place. An unbalanced journal, a missing account, and a staff denial do not consume the key. The stored row has no delete method. The response and the audit do not echo the key.

A collection uses the same store with action `COLLECTION_RECEIVED`. The key is the provider event id. The row is written in the same transaction as the journal and the collection. A pending, failed, or unknown webhook does not write that row.

## Collections

The owner opens a collection account with an asset clearing account and a liability merchant account. The fake bank assigns the external reference. A `SUCCESSFUL` webhook posts one journal: debit clearing, credit merchant, currency `NGN`, amount in kobo. The journal's `created_by` is the owner who opened the account. The same event again returns the first collection. Staff cannot open a collection account. A collection id from another organization returns `Collection was not found.`

## Local database

The application connects to `jdbc:postgresql://localhost:5432/brewpay_db` unless `BREWPAY_DB_URL` is set. The user is `BREWPAY_DB_USER` (default `brewpay`) and the password is `BREWPAY_DB_PASSWORD` (default empty). This repo does not create `brewpay_db`. `./mvnw verify` does not need it: the tests start PostgreSQL 16 in Testcontainers. A database that already applied `V1` and has user rows must be recreated before `V2`, because `V2` adds required password and role columns. `V3` adds the ledger tables on top of `V2`. `V4` adds the idempotency table on top of `V3`. `V5` adds the collection tables on top of `V4`. The fake webhook secret is `BREWPAY_FAKE_BANK_WEBHOOK_SECRET`. The default in this file is only for the local fake bank. It is not a Kolomoni credential.
