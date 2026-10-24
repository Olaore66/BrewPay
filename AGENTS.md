# BrewPay agent rules

This file is the fence for every AI session. Read it before editing.

## This slice

Slice 2 is organization and user. Slice 1 still holds: build, CI, formatter, package rules, correlation id, and one error shape. Do not add login, sessions, permissions, audit, ledger posting, webhooks, or a Kolomoni client in this slice.

## Rules that already have tests

- Every HTTP response carries `X-Correlation-Id`. A missing or unsafe header is replaced. The id is also on the error body.
- Every error body is `code`, `message`, `correlationId`, `details`. A 500 message is generic. The exception text stays in the log.
- `ledger`, `payments`, `identity`, `access`, `audit`, `reconciliation`, and `notifications` do not depend on `com.brewpay.banking.kolomoni`. The port package `com.brewpay.banking` does not depend on the adapter either.
- A user read or write is scoped by the organization id in `TenantContext`. Organization A asking for organization B's user gets the same not-found as an unknown user. `UserRepository` has no finder that omits the organization id. Email is unique per organization, stored lowercase.

## How to change the code

- One rule per change. Add or update the test first.
- Spotless is part of `./mvnw verify`. Format with `./mvnw spotless:apply`.
- Do not edit a merged Flyway migration. `V1__organization_and_user.sql` is the organization and user schema. Add `V2__...` for the next change.
- Provider payloads stay in `banking.kolomoni` when that adapter exists. Domain code calls the port.
- Do not log secrets, BVNs, tokens, or raw provider payloads.
- Child reads go through `UserRepository` methods that take `organizationId`. Do not add `findById`, `findAll`, or extend `CrudRepository` / `JpaRepository` on that interface.

## Tenant header

`X-Organization-Id` is a temporary stand-in. Slice 3 will set `TenantContext` from the session. Until then, any caller who can choose the header can choose the organization. The tested rule is the query scope, not authentication. `POST /api/v1/organizations` does not use the header. It creates the organization and its owner in one transaction.

## Local database

The application connects to `jdbc:postgresql://localhost:5432/brewpay_db` unless `BREWPAY_DB_URL` is set. The user is `BREWPAY_DB_USER` (default `brewpay`) and the password is `BREWPAY_DB_PASSWORD` (default empty). This repo does not create `brewpay_db`. `./mvnw verify` does not need it: the organization tests start PostgreSQL 16 in Testcontainers.
