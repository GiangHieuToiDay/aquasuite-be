# WORK-026 ? COD using existing transactions

## Metadata
- Owner/Owning Module: Finance; Fulfillment supplies delivery snapshots through its public API.
- Risk: L ? money, authorization, concurrency and API/schema change.
- Design Review: APPROVED by repository owner in chat, 2026-11-12: use transactions, combine review and cash receipt, remove V32 and new COD tables.

## 1. Goal and scope
Use existing transactions for declarations and cash receipt. No new business tables.
Each declaration is received in full once. Preliminary acceptance, partial remittance and multi-collection remittance documents are removed.

## 2. Actors and security
SHIPPER declares and reads their own receipts. ACCOUNTANT and SHOP_OWNER can receive/reject and read all; SALES_STAFF can read. No actor can receive their own declared cash. Actual authenticated IDs are recorded as created_by/approved_by. No secrets in logs or artifacts.

## 3. Business rules
- Declaration creates RECEIPT/COD/CASH/PENDING with delivery_id; does not mark the order paid.
- Accept confirms full physical handover, changes the same row to VERIFIED and updates order payment status atomically.
- Reject requires a reason and changes to REJECTED; no payment is applied.
- Status/version: PENDING/0 -> VERIFIED/1 or REJECTED/1. Terminal replay by same actor with same decision is harmless; changing a decision conflicts.
- Amounts are positive integer VND, capped by delivery COD and outstanding order amount after other pending COD.
- Pending COD represents shipper-held cash, reduces customer-collectible amount and blocks duplicate cash/bank collection. Only VERIFIED counts as paid.

## 4. Data and migration
- Existing transactions gains nullable delivery_id, scalar UUID in Java, FK to deliveries and an index.
- Existing idempotency_key unique index, created_by, approved_by, approved_at, transaction_date, description and reject_reason are reused.
- V32 source removed by user request. V34 adds delivery_id and guards using transactions, not legacy COD tables. V33 was already removed in the separate category task.
- IMPORTANT: configured database has successful V32 and V33 history entries; three COD tables were empty at inspection. Existing database cleanup/history reconciliation requires explicit human approval; never automatically repair Flyway or delete data.
- A reviewed local cleanup script is prepared separately. It refuses to drop non-empty legacy COD tables. No conversion of financial data is silently attempted.

## 5. Contracts
Base /api/v1/finance/cod. Existing claim/list/detail/accept/reject/balance paths remain. Collection response now exposes status=PENDING/VERIFIED/REJECTED; collectionId is the transaction UUID. /remittances and /collections/{id}/remittances are removed. No Java/event contract changes.
See docs/contracts/uc30-current-api.md for payloads and client migration.

## 6. Transactions and concurrency
Shared payment-key advisory lock then order row lock, then delivery/transaction lock. Read scalar order UUID before loading the transaction entity to avoid stale persistence state. All status/payment updates share one transaction. Database receipt guards serialize on orders; pending COD prevents order total/customer/cancellation changes. Idempotency keys share namespace with ordinary payments; mixed-payload replay conflicts.

## 7. Acceptance criteria
- No new COD tables on fresh migration; transactions references deliveries.
- Declare does not pay, accept fully pays once, reject releases pending balance.
- Owner/accountant permissions and shipper ownership retained; self-approval denied.
- Cash/bank payment cannot charge pending COD again.
- Concurrent declarations cannot exceed delivery/order balance.
- Existing shared/staging DBs require separate reviewed reconciliation before this migration set is deployed.

## 8. Open decisions
No business decisions remain. Applying cleanup to the already-migrated local database is a separate approval gate.
