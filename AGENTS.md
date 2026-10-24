# BrewPay agent rules

This file is the fence for every AI session. Read it before editing.

## This slice

Slice 1 is the platform fence: build, CI, formatter, package rules, correlation id, and one error shape. Do not add accounts, ledger posting, webhooks, auth, or a Kolomoni client in this slice.

## Rules that already have tests

- Every HTTP response carries `X-Correlation-Id`. A missing or unsafe header is replaced. The id is also on the error body.
- Every error body is `code`, `message`, `correlationId`, `details`. A 500 message is generic. The exception text stays in the log.
- `ledger`, `payments`, `identity`, `access`, `audit`, `reconciliation`, and `notifications` do not depend on `com.brewpay.banking.kolomoni`. The port package `com.brewpay.banking` does not depend on the adapter either.

## How to change the code

- One rule per change. Add or update the test first.
- Spotless is part of `./mvnw verify`. Format with `./mvnw spotless:apply`.
- Do not edit a merged Flyway migration. This slice has no schema yet.
- Provider payloads stay in `banking.kolomoni` when that adapter exists. Domain code calls the port.
- Do not log secrets, BVNs, tokens, or raw provider payloads.

## Local database

The intended database is `jdbc:postgresql://localhost:5432/brewpay_db`. This slice does not open a connection. Credentials come from `BREWPAY_DB_USER` and `BREWPAY_DB_PASSWORD` when a later slice wires the pool.
