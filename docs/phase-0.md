# Phase 0 decisions

PRD section 42 is the list of questions to settle before production. Kolomoni has not answered them. These decisions follow the operating model published by Anchor, Paystack, Maplerad, and Synctera. They are the contract this codebase builds to. A Kolomoni answer replaces the matching line. It does not leak into the ledger.

## What those businesses do

Anchor, Paystack, and Maplerad are the Nigerian products closest to this platform. Synctera is the clearest write-up of the bank-and-ledger split those products also use.

- The bank owns the deposit account. The platform keeps a sub-ledger of each merchant. Paystack links a dedicated virtual account to the platform business. Synctera holds customer funds in an FBO account titled for the benefit of the end customers, and the fintech ledger is the customer record. Anchor settles a virtual NUBAN into a deposit account or sub-account.
- The provider performs KYC and KYB. The platform collects the profile and stores the verdict. Anchor starts a customer at tier 0 and approves tier 1 from BVN through `/verification/individual`, then sends `customer.identification.approved`, `.rejected`, or `.error`. Maplerad creates tier 0 with name, email, and country, then upgrades with phone, address, and NIN. Synctera refuses accounts and transactions until `verification_status` is `ACCEPTED`.
- A webhook is a hint. Paystack signs the raw body with HMAC-SHA512 in `x-paystack-signature` using a secret that is not an API key. Synctera signs `{timestamp}.{raw body}` with HMAC-SHA256. Anchor sends `nip.inbound.received` when money is pending and `nip.inbound.completed` when it has settled, and tells the client to give value on the completed event. Paystack's dedicated-account guide says the only way to learn that a transfer arrived is the webhook, and the payment is still verified.
- A reversal is a new event. Paystack emits `transfer.reversed`. This ledger already posts a reversal as a new journal.
- Fees are a separate movement in minor units. Anchor's custom fee is an amount in kobo that settles to the organization's revenue account. Paystack's dedicated-account price is 1 percent capped at 300 naira. That price is Paystack's, not BrewPay's.
- Limits follow the verification tier. Unverified customers cannot hold an account or move money.

Sources: [Anchor individual KYC](https://docs.getanchor.co/docs/individual-customer-kyc), [Anchor account numbers](https://docs.getanchor.co/docs/account-numbers), [Anchor payments](https://docs.getanchor.co/docs/overview-10), [Anchor custom fee](https://docs.getanchor.co/docs/custom-fee), [Paystack dedicated virtual accounts](https://paystack.com/docs/payments/dedicated-virtual-accounts/), [Paystack webhooks](https://paystack.com/docs/payments/webhooks/), [Maplerad customer tiers](https://maplerad.dev/docs/create-maplerad-customer), [Synctera KYC](https://docs.synctera.com/docs/kyc-kyb-verification), [Synctera accounts on the bank core](https://docs.synctera.com/docs/accounts-on-the-banks-core).

## Answers we will build to

1. Virtual accounts. The first product is one permanent collection NUBAN per verified merchant. It is an external reference on our collection account. Dynamic per-invoice accounts wait.
2. Static first. Dynamic is a later provider capability, not a branch in the ledger.
3. Account ownership. Kolomoni owns the deposit account. BrewPay's ledger is the merchant sub-ledger. Customer funds are not BrewPay's own money.
4. KYC. Kolomoni, behind `KycProvider`, performs KYB and ongoing monitoring. BrewPay collects the business profile, stores each verdict, and does not approve itself.
5. Webhooks. The adapter checks an HMAC of the raw body with a webhook secret before the domain sees the event. The secret is not an API key. The fake bank already does this.
6. Verify API. A signed webhook is not enough to post a journal. The next money slice calls the provider's payment read, and posts only when that read is settled. This slice does not add that call.
7. Reversals. A reversal is a new journal. The original lines stay. A provider reversal arrives as a new event.
8. Transfers. The owner submits once per idempotency key. A timeout stays `UNKNOWN` until a later status read or webhook. The Kolomoni status payload stays in the adapter.
9. Limits. No naira cap is invented here. The first limit is the verification gate: an owner whose KYB is not `APPROVED` cannot use the ledger, collections, transfers, name enquiry, beneficiaries, or reconciliation.
10. Settlement. Give value when the provider says the credit has settled, not when it says a credit is incoming. Settlement files are a later reconciliation input. No T+1 number is invented.
11. Rate limits and uptime. Kolomoni must publish these. They are not guessed.
12. Onboarding. BrewPay completes its own partner KYB and sandbox certification with Kolomoni before production. Each merchant then submits KYB through the API.
13. Fees. A fee is a separate journal in kobo into a platform revenue account. The percent and cap wait for Kolomoni. Paystack's 1 percent cap is not copied.
14. Compliance that stays here. Tenant isolation, the audit log, staff permissions, and refusal to treat a webhook as final. Transaction monitoring of the bank account stays with Kolomoni.
15. Support. An unknown transfer and a reconciliation finding are the incident records. No escalation clock is invented.

## What this slice changes

`POST /api/v1/organizations` creates the business as `PENDING` with KYC `NOT_SUBMITTED`. The owner can sign in and add staff. `POST /api/v1/kyb-submissions` sends the profile to `KycProvider`. The local fake approves unless a test scripts `PENDING` or `REJECTED`. `APPROVED` sets the organization to `ACTIVE`. Until then, money routes return `403` with `Business verification is not complete.` Staff still receive the permission denial, not this message.
