# UC36 — History and approved reservation release

## Metadata
- Work ID: UC36-CURRENT. Risk: L (stock, authorization, cross-module transactions).
- Owners: sales composes UC26 decisions; inventory owns reservation balances/history.
- Scope authorized by the user on 09/11/2026: implement UC36 first against already persisted valid UC26 approvals; no approval implementation.
- Design authorization: user-confirmed scope and role decisions in this session. Earlier UC36 draft accountant-approval wording is superseded for this scope.

## 1. Scope
Use the existing UC29 order route for reservation history and execution of approved releases. One warehouse, WH_MAIN; no client warehouse or quantity input. No new list screen, finance flow, approval route, expiry scheduler, dispatch, or frontend repository changes.

## 2. Security
Sales can read every order's UC36 history. SHOP_OWNER, ADMIN, SUPER_ADMIN and ACCOUNTANT can also read. Only SHOP_OWNER executes an already approved release; this is execution/retry, never a second approval. The stored decision must be APPROVED with reviewedBy/reviewedAt, and its reviewer must resolve through IAM to SHOP_OWNER. The executing actor comes from IAM. UC36 responses contain no payment amounts or financial documents.

## 3. Rules
Support FULL_CANCELLATION and ITEM_CHANGE containing only REMOVE/REDUCE. Reject address changes, swaps, malformed/duplicate lines, wrong-order decisions, unapproved decisions and affected consumed reservations. Release only the selected line's held quantity above the retained order quantity; waiting stock is not physical held stock. A target reduced from 8 to 6 with only 5 held releases zero. A target reduced to 2 releases 3. Legacy aggregate holds are never adopted or released. Reject non-main warehouse data for manual reconciliation.

After a pending-cancel or approved quantity-changing decision, UC29 allocation against its old immutable selection is blocked pending an explicit future reconciliation flow. UC36 does not rewrite order quantities, selection targets, money or invoices. Historical requested/waiting quantities remain the original selection; no new allocation is allowed against them.

## 4. Persistence
Reuse sales_reservation_operations, keyed by UC26 decision ID, with order ID, executor/time and a UC36_RELEASE: prefixed JSON result snapshot. V38 migrates existing V35 results and removes the separate sales_reservation_releases table. The decision remains the approval authority; the existing ledger records completed execution including zero-release results.

## 5. HTTP contracts
Base: /api/v1/sales/orders/{orderId}/stock-reservations
- GET /history: authorized staff; returns reservations, inventory activities and UC26 request/reviewer metadata with PENDING_APPROVAL, NOT_APPLICABLE, PENDING_RELEASE or COMPLETED execution state. No money fields.
- POST /releases/{decisionId}: SHOP_OWNER; no body; returns persisted release result. Retry returns the same result, including the original executor/time.
- Standard ApiResponse. AUTH_002 for role denial; SALES_DEPOSIT_001 for absent order; UC36_001 (404) missing decision; UC36_002 (409) invalid approval; UC36_003 (409) unsupported/stale stock/decision. Existing INV_RES errors propagate and roll back.
- Reuse inventory.api.StockReservationApi.findByOrder/history/release, entity-free DTOs, REQUIRED transaction. No changed inventory Java contract.

## 6. Transactions and recovery
Lock order first, then decision. Serialize UC29 and UC36 through the same order row. Execute sorted inventory releases and store the result in one transaction; any failure rolls everything back. Stable operation IDs derive from decision + reservation. GET uses REPEATABLE_READ and makes no writes. No background execution is claimed until UC26 publishes/implements approval integration; owner can execute/retry this endpoint.

## 7. Verification
- Staff role/access and no financial fields.
- Reject pending/rejected/wrong-order/non-owner approvals, swaps, consumed or wrong-warehouse reservations.
- Partial/full release, zero held/partially waiting lines, retry, order allocation guard.
- PostgreSQL atomicity, repeat/concurrent execution, history and unchanged physical/legacy stock.

## 8. Limitations
UC26 currently lacks approve/submission implementation. Real end-to-end approval remains dependent on its owner. Frontend lives in another repository and is outside this backend task.


## 9. Actor display names (10/11/2026)
User authorized backend/frontend update to display names instead of raw actor IDs. GET history adds `actorNames: { "<UUID>": "<display name>" }` for activity actors, request creators, reviewers and release executors present in that history. Sales collects distinct IDs after the existing authorization check and calls the existing `IamApi.getUserById` for each distinct actor, through a Sales-owned helper. IAM prefers nonblank fullName, otherwise username; absent users are omitted. Existing IDs, permissions, financial/stock behavior and persisted release JSON remain unchanged. Names are current display values, not historical snapshots. No new HTTP account lookup or broader account-list access. FE falls back to a neutral unavailable-name message when a name is missing.

## 10. Current-branch adaptation (2026-11-12)
Design scope confirmed by repository owner: add the missing UC36 functions without modifying other functions of aquasuite-test. Existing ReservationAllocationRecovery/Runner retry remains untouched. No branch merge or cherry-pick, no changes to IAM, inventory implementation/API, finance/COD, quotations, customer/credit logic, delivery or historical migrations. This spec supersedes only the UC36 implementation scope; it does not change the UC26 approval process.

Only three existing production files change within the reservation function: OrderModificationRequestRepository gets one read query; OrderStockReservationStore gets one review-required query; OrderStockReservationService gets one allocation guard. The guard blocks only quantity-changing decisions in PENDING_CANCEL/APPROVED, preventing allocation against the old target after UC36. All other existing paths remain as-is.

The new ReservationActorNames helper uses existing public IAM APIs; missing users are omitted. Name lookup runs with the history transaction suspended so a missing IAM user does not poison the read transaction. Names are display metadata, not a new authorization mechanism.

V35/V36 stay unchanged as applied history. V38 is the forward migration required for this implementation: it preserves results, aborts on operation-ID conflicts, then drops the obsolete result table. No app DB schema/data is changed during this coding task. Zero-release/concurrent retries use the existing Sales operation ledger. V36 CONSUME support remains necessary for Delivery reservation consumption.

Verification covers role/security, malicious decision data, exact quantities, zero release, retry, rollback, concurrent execution, unchanged physical/legacy stock, and existing UC29 regression. A before/after file hash audit checks that unrelated pre-existing working tree changes were preserved.
