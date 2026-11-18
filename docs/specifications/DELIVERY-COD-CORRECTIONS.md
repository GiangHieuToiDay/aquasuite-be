# DELIVERY-COD-CORRECTIONS — COD, delivery drafts and reservation diagnostics

## Metadata
- Work ID: DELIVERY-COD-CORRECTIONS
- Date: 2026-11-17
- Owning modules: fulfillment, finance, inventory; Sales is consumed through existing public APIs only.
- Risk: L — money, inventory, authorization, concurrency and HTTP contracts.
- Design Review: owner confirmed role matrix and partial COD rule on 2026-11-17; technical self-review completed during implementation. Independent release review not claimed.
- Decision Owner: repository owner / module owners.

## 1. Goal and scope
Prevent COD above the order's collectible balance, support editing delivery drafts,
explain stock/reservation failures and return actionable delivery validation messages.
User screenshots are symptoms; they do not establish a database constraint failure's cause.
Frontend changes are outside this backend repository's authorized scope.
Owner clarified that Sales order creation is outside their responsibility; its DTO and dedicated new validation tests were restored/removed.
No application database repairs, migration execution, payment rewrites or automatic release of holds.

## 2. Actors and security
Current backend permits ACCOUNTANT/SHOP_OWNER to create/confirm/cancel drafts and assign deliveries.
SHIPPER may act only on assigned deliveries; COD claims require DELIVERED.
Proposed draft edit uses the current ACCOUNTANT/SHOP_OWNER access rules unless the owner specifies otherwise.
Owner confirmed this actor/action matrix on 2026-11-17; retain these permissions.
No cross-module internal imports or JPA associations will be introduced.

## 3. Business rules
1. COD is money to collect, not a shipping fee. It is an integer VND amount >= 0.
2. Compute remaining order debt from total minus VERIFIED receipts. Deduct PENDING COD and
   unclaimed amounts of confirmed delivery commitments once to obtain capacity for a new commitment.
   Preserve the current Finance capacity algorithm, including FAILED confirmed commitments.
3. Validate COD at draft creation, draft update and confirmation. A fully paid order accepts COD = 0 only.
   Candidates expose the current maximum/default COD for the form. Reads are advisory; writes recheck under locks.
4. Allow any amount from zero through the maximum, inclusive. The form defaults to that maximum.
5. Claims continue to reject amounts above remaining order debt after pending COD and above the delivery's remaining COD.
   Owner confirmed that a VERIFIED partial claim does not close the delivery: allow further claims
   only up to the remaining order and delivery balances; once either balance is zero, reject further claims.
6. Edit only DRAFT with confirmedAt null. Proposed editable fields: recipient name/phone/address,
   scheduled date, COD, note and line quantities/selection from the original invoice.
   Preserve delivery ID/code, order/invoice, batch and creator. No stock effect until confirmation.
7. Source invoice remains ISSUED; quantities fit both remaining invoice and order quantities.
   Serialize edit/confirm/cancel so a confirmed delivery cannot be edited by a concurrent request.
8. Consume only the exact order line's recorded WH_MAIN hold, then free stock.
   Never adopt aggregate or another order's holds. Determine the reported defect with a regression
   reproducer before changing reservation accounting; preserve incompatible UC29 targets for explicit review.
9. Delivery validation returns field paths and Vietnamese field-specific messages without SQL/constraint internals.
   Do not change Sales order creation rules or its request DTOs.

## 4. Data and migration
No schema change or existing-data mutation is planned. Any discovered need for a migration requires review.
No archived/cancelled workflow artifacts are changed.

## 5. Contracts
- Existing candidates response: additive maxCodAmount and per-line dispatchableQty for the form.
- PUT /api/v1/fulfillment/deliveries/{id}/draft: validate editable payload; return existing Result shape.
- GET /api/v1/finance/cod/deliveries/{deliveryId}/claim-capacity: own-shipper/staff remaining capacity.
- Keep POST creation idempotency; changed POST payload must not become an implicit edit.
- Add specific business errors for COD limit/settled delivery, invalid source/quantity and noneditable state.
- Preserve ApiResponse and errors[].field/message. Do not expose database details to clients.
- Update the UC39 contract to supersede immutable-draft guidance after design approval.

## 6. Transaction and failure safety
Retain order-first locking, then delivery/source invoice/inventory locks consistent with existing flows.
Capacity calculation joins the mutation transaction. Draft updates and confirmation serialize on the same order/delivery.
Any failed line rolls back delivery, stock, reservation consumption and ledger changes together.
Exact retries remain idempotent; updates do not duplicate lines or change issued stock.

## 7. Acceptance and verification
- Fully paid order: reject positive COD at create/edit/confirm; allow zero.
- Partially paid order: accept maximum or smaller amounts; reject maximum + 1 VND.
- Other pending/confirmed COD commitments do not allow double allocation.
- Shipper claim: permit the remaining balance after an accepted partial receipt; reject over remaining order or delivery balance and reject when either is zero.
- Draft edit: succeeds for authorized actors; rejects confirmed/cancelled deliveries and concurrent confirmation.
- Own held stock is visible and consumed exactly once; another order's hold remains unchanged.
- Reservation/stock failures roll back all lines; reproduce screenshot's DB_001 before claiming it fixed.
- Missing/invalid delivery fields return actionable field messages. Sales validation is outside scope.
- Targeted service/security tests plus disposable PostgreSQL concurrency/reservation tests where available.
- Frontend integration must separately default COD, enforce the maximum, filter nonclaimable deliveries,
  expose draft edit and display errors[].message.

## 8. Open decisions
- Expected delivery role/action matrix: owner confirmed ACCOUNTANT/SHOP_OWNER for create/edit/confirm/assign; shipper only assigned deliveries.
- Further claims after a VERIFIED partial claim: owner approved collecting only the remaining amount on 2026-11-17.
- Draft edit uses the recipient/schedule/COD/note/items scope described above; original invoice/order/warehouse remain fixed.
- Reproduction data or server exception for screenshot's DB_001: not yet available; no root cause asserted.
