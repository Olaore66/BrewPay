# Failure map

When something breaks, name the rule, then open the package. The test name is the proof.

| What you see | Broken rule | Where to look | Test |
|---|---|---|---|
| Response has no correlation id, or it echoes junk from the client | Every response carries one safe id | `platform.correlation` | `PlatformHttpContractTest` |
| Error JSON gained a new field, dropped `details`, or a 500 leaked the exception text | One error shape | `platform.error` | `PlatformHttpContractTest` |
| A domain package imports Kolomoni | The bank stays behind the port | The importing package, and `banking.kolomoni` | `PackageRulesTest` |
| Organization A receives organization B's user, or a user lookup succeeds with only a user id | Child reads include the organization id | `identity`, `platform.tenant` | `OrganizationIsolationTest`, `UserRepositoryContractTest` |
| A caller chooses the organization with a header, or a revoked or expired token still works | The session chooses the organization | `identity` | `LoginSessionTest`, `OrganizationIsolationTest` |
| Wrong password, unknown email, and unknown organization return different messages | Login failure has one message | `identity` | `LoginSessionTest` |
| Staff adds a user, or staff reads the audit | Only the owner can add a user and read the audit | `access`, `identity`, `audit` | `LoginSessionTest` |
| An audit row is missing, editable, or contains a password or session secret | Audit is append-only and records these actions | `audit` | `LoginSessionTest`, `AuditRepositoryContractTest` |
| An unbalanced journal is stored, a reversal changes the original lines, or the same journal is reversed twice | A journal is stored only when debits equal credits, and a reversal is a new journal | `ledger` | `LedgerRulesTest`, `LedgerRepositoryContractTest` |
| Organization A reads organization B's journal or account | Ledger reads include the organization id | `ledger` | `LedgerRulesTest` |
| The same idempotency key posts two journals, or one organization receives another's stored result | The same key, organization, and action returns the first journal | `payments` | `IdempotencyRulesTest`, `IdempotencyRepositoryContractTest` |
| A domain package imports the fake bank, or the port imports an adapter | The bank stays behind the port | The importing package, `banking.fake`, `banking.kolomoni` | `PackageRulesTest` |
| The same webhook credits the merchant twice, or one organization reads another's collection | A paid collection posts one journal, and a duplicate webhook returns that collection | `payments`, `banking.fake` | `CollectionRulesTest`, `CollectionRepositoryContractTest` |
| A transfer timeout is stored as success or failure, or the same key submits it twice | A transfer timeout stays UNKNOWN and does not move money | `payments`, `banking.fake` | `TransferRulesTest`, `TransferRepositoryContractTest` |

Later slices add a row for reconciliation mismatches. Do not remove a row when its test still exists.
