# UC30 ? COD receipts in transactions

Owner-approved simplified flow, 2026-11-12. No additional COD business tables.

## Flow
1. SHIPPER completes an assigned delivery and declares collected cash.
2. One RECEIPT/COD/CASH transaction is created PENDING; the order remains unpaid.
3. ACCOUNTANT or SHOP_OWNER physically receives the entire declared amount and calls accept. The same transaction becomes VERIFIED and contributes to order payment.
4. Reject requires a reason, changes the receipt to REJECTED and does not pay the order.

There is no preliminary acceptance step, partial handover, allocation or combined remittance document.

## Endpoints
Base /api/v1/finance/cod; authenticated JWT, ApiResponse, success HTTP 200.

| Method/path | Role | Body / response |
|---|---|---|
| POST /collections | SHIPPER | Claim -> Collection |
| GET /collections | Staff or SHIPPER | shipperId/orderId/status/page/size -> Page |
| GET /collections/{id} | Staff or owning SHIPPER | Collection |
| POST /collections/{id}/accept | ACCOUNTANT, SHOP_OWNER | Review -> full cash receipt VERIFIED |
| POST /collections/{id}/reject | ACCOUNTANT, SHOP_OWNER | Review with reason -> REJECTED |
| GET /orders/{orderId}/balance | Staff | Balance |

Staff: ACCOUNTANT, SALES_STAFF, SHOP_OWNER. Self-accept/reject is denied even when actor has multiple roles.

Claim: {"deliveryId":"UUID","amount":8500000,"collectedAt":"2026-11-12T14:00:00+07:00","note":"Cash collected","idempotencyKey":"UUID"}.
Review: {"expectedVersion":0}; rejection also requires nonblank reason <=1000 characters.
Money: positive integer VND, up to 13 integer digits. collectedAt <= now+5 minutes; note <=1000.

Collection fields: collectionId (= transactionId), deliveryId, orderId, customerId, shipperId (= createdBy), amount, status, collectedAt, submittedAt, reviewedBy, reviewedAt, rejectReason, note, version.
Status: PENDING, VERIFIED, REJECTED; version: 0 for pending, 1 for decided. Retry the original claim with identical key/payload; changed payload conflicts. Terminal decision retry is safe only for the same actor and same decision/reason.

Lists: page>=0, size 1..100 (default 20); optional status uses the three transaction statuses above.

## Client changes from the former V32 design
- Remove preliminary review/accept UI: accept now means actual receipt of cash.
- Remove POST /remittances and GET /collections/{id}/remittances; these APIs no longer exist.
- Use status instead of reviewStatus; remove partial allocation and remittedAmount/heldAmount/remittanceStatus fields.
- Do not translate old ACCEPTED into VERIFIED: that would falsely claim cash was received.

## Balance
storeOutstanding = order total - VERIFIED receipts.
shipperHeld = pending COD transaction amounts (declared, not yet physically received).
customerCollectible = max(storeOutstanding - shipperHeld,0).
pendingClaimAmount = pending COD amounts.
Do not charge pending COD to the customer again. Existing UC24 reminder/blacklist policy is outside this slice and must be considered before wider rollout.

## Schema and rollout
V34 adds only transactions.delivery_id, FK/index/checks and balance guards; it creates no tables.
Existing V32/V33 history must be reconciled separately with explicit approval before startup after removing those source files. No automatic Flyway repair, history edits or dropping tables during application startup.
COD_001=400 invalid data, COD_002=409 balance/state/version conflict, COD_004=409 changed-key replay. Database guards return DB_001/409. Delivery photo storage requirements remain in WORK-025.
