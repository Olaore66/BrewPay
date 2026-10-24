# Failure map

When something breaks, name the rule, then open the package. The test name is the proof.

| What you see | Broken rule | Where to look | Test |
|---|---|---|---|
| Response has no correlation id, or it echoes junk from the client | Every response carries one safe id | `platform.correlation` | `PlatformHttpContractTest` |
| Error JSON gained a new field, dropped `details`, or a 500 leaked the exception text | One error shape | `platform.error` | `PlatformHttpContractTest` |
| A domain package imports Kolomoni | The bank stays behind the port | The importing package, and `banking.kolomoni` | `PackageRulesTest` |

Later slices add rows for tenant leaks, unbalanced journals, duplicate webhooks, and provider timeouts. Do not remove a row when its test still exists.
