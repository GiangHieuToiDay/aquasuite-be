# UC39 — Order to Delivery API (additive implementation)

## Scope
Existing unrelated behavior is preserved; additive public methods and Delivery changes are allowed.
ACCOUNTANT/SHOP_OWNER create, confirm, cancel. They agree offline; no deposit or full-payment gate.
Only PROCESSING orders with no unresolved modifications and WH_MAIN are supported.
No new business tables, trip/stop model, order completion, payment mutation, automatic return or retry FAILED.

## Endpoints under /api/v1/fulfillment
- GET /orders/{orderId}/delivery-invoices: issued invoice summaries for the selected order (invoiceId, invoiceCode, orderId, type, status); empty list when no issued invoice exists.
- GET /sales-invoices/{invoiceId}/delivery-candidates: source invoice and remaining quantities.
- POST /sales-invoices/{invoiceId}/deliveries: create a DRAFT linked to an existing invoice.
- GET /orders/{orderId}/delivery-candidates: ordered/issued/remaining, own held and free stock; eligible is advisory.
- POST /orders/{orderId}/deliveries: create immutable DRAFT. Same deliveryId/actor/payload replays; changed payload conflicts.
- POST /deliveries/{id}/confirm-issue: empty body. DRAFT -> DISPATCHED, confirmedBy/At and dispatchDate set.
- POST /deliveries/{id}/cancel-draft: empty body. DRAFT -> CANCELLED, no stock effect.

Mutation response fields: deliveryId, deliveryCode, orderId, batchNo, deliveryStatus, confirmedAt, salesInvoiceId.
Read existing GET /deliveries/{id} for full details. All JSON responses use ApiResponse, success 200.

Example create body (client generates and retains deliveryId UUID for retry):
```json
{
  "deliveryId": "UUID",
  "warehouseCode": "WH_MAIN",
  "recipientName": "Nguoi nhan",
  "recipientPhone": "0900000000",
  "deliveryAddress": "Dia chi giao",
  "scheduledDate": "2026-11-13T09:00:00+07:00",
  "codAmount": 5000000,
  "note": "Giao dot 1",
  "items": [{"orderItemId": "UUID", "quantity": 5}]
}
```
Quantities: positive, max10 integer digits and2 decimals. Money: nonnegative integer VND, max13digits.
At most100 distinct order lines; server derives variant and inventory. batch_no monotonically increments per order,
including canceled drafts. Creating a draft does not reserve or deduct stock and does not guarantee confirm success.

## Stock and reservation invariants
Confirm locks order -> delivery -> source line/inventory, validates remaining and COD again, then issues all lines
in one transaction. Each movement ID is the delivery item ID. Repeated confirmation by the same confirmer returns
stored result; a different confirmer is rejected. Any line failure rolls back stock, consumption, ledger and delivery.
FAILED deliveries remain issued; no automatic increase to remaining quantity or on-hand.
Tracked holds from the same line are consumed first; only free units may satisfy the rest. Other orders' holds stay intact.
For previously unreserved lines, a consumed reservation entry records issued units so later UC29 cannot hold them again.
Existing immutable UC29 selections with incompatible targets require review; never silently rewrite their requested target.
Legacy aggregate holds not matching the reservation ledger are blocked rather than attributed to this order.
Serial-tracked SKUs are explicitly rejected until authoritative serial inventory is available; no free-text serial bypass.

## COD
codRequired is derived from codAmount>0. Finance computes available amount as total minus VERIFIED receipts,
minus pending COD and undeclared portions of previously confirmed COD commitments. Declared pending/verified
cash is subtracted from its own delivery commitment once, not twice. FAILED confirmed deliveries retain commitments.
Later external payments may reduce collectible balance; existing UC30 still validates actual claim against current debt.
Confirmation never marks the order paid or completed. No new money transaction is created here.

## Errors
SYS_002/400 invalid request; AUTH_001/401 missing login; AUTH_002/403 wrong role; SYS_004/404 missing data;
DB_001/409 conflicts in state, quantities, stock, reservation reconciliation, serial support, COD or replay.

## Deployment
V36 permits CONSUME in the existing reservation operation constraint and adds unique(order_id,batch_no) for nonnull batches.
No old migration is edited. Duplicate legacy batches require human reconciliation before migration; no automatic data edits.
Apply via normal reviewed Flyway startup only after the separately pending V32/V33 history reconciliation.
This implementation task does not run migration against the application database or restart the running backend.
Central audit integration, warehouse returns and the pre-existing broader test suite failures remain separate work.

## Inventory API integration clarification
Owner clarified that adding methods to existing files is allowed; existing method bodies must remain unchanged.
Delivery now calls InventoryApi.issue(StockDispatchCommand), implemented by a delegating method on InventoryServiceImpl.
Stock-out logic remains in internal InventoryDispatchService; the separate public InventoryDispatchApi is removed.
ObjectProvider resolves the new internal service only when issue is invoked, so existing narrow test/application
contexts using legacy inventory methods do not acquire an eager dependency on the new dispatch service.
No changes to existing inventory method behavior, HTTP endpoints, schema or issue business rules in this refactor.

## Legacy hold compatibility fix — 2026-11-12
Supersedes the strict-equality legacy-hold block above. UC39 now requires aggregate reserved_qty >=
recorded held total, matching the existing deferred-deposit compatibility policy. A positive difference
is preserved as unowned legacy stock; only the exact order line's recorded held quantity is decremented.
Any unreserved part of a shipment must fit on_hand_qty - reserved_qty, so unowned/other-order holds
cannot be used as free stock. Negative/incomplete aggregate coverage remains blocked.
New errors: INV_DISPATCH_001 inconsistent hold coverage; INV_DISPATCH_002 insufficient usable stock;
INV_DISPATCH_003 unsupported serial-tracked SKU. No database repair, data write or migration required by this fix.

## Invoice source — 2026-11-13
User scope: Delivery consumes existing invoices only; invoice creation/splitting is outside this change.
One order may have multiple invoices; one delivery belongs to one invoice. Each invoice may have multiple delivery batches.
Only invoices in ISSUED state are accepted. Invoice lines must reference matching order items and variants.
At create and confirm, quantities cannot exceed either the invoice's unissued quantity or the order's unissued quantity.
For example, invoices containing 4 and 6 units of a 10-unit order can each produce their own delivery.
Drafts do not consume either allowance; confirmation rechecks both under the order/invoice locks.
Cancelled drafts do not consume allowance; FAILED confirmed deliveries still do.

The order-based endpoints remain compatible only when exactly one eligible invoice exists for that order.
If several exist, callers must select an invoice and use the invoice-based endpoints; no invoice is fabricated.
The create JSON above still uses orderItemId to identify source lines. Candidate responses include salesInvoiceId
and invoiceCode; existing delivery summary/detail responses include salesInvoiceId.
COD remains capped and settled by order, with the existing verification workflow.

V37 adds nullable deliveries.sales_invoice_id and a composite foreign key enforcing invoice/order consistency;
it creates no business table. Legacy confirmed deliveries are retained without automatic backfill.
Legacy drafts can be confirmed only if their stored invoice is valid or exactly one eligible invoice can be resolved.
V37 has been exercised on a disposable PostgreSQL database, not applied to the application database.
Frontend callers must select the invoice and use the new endpoints after migration and backend deployment.

## Issued-only source — 2026-11-14
Invoice draft creation, editing and issuance are owned by the Order/invoice developer.
Fulfillment never issues invoices. ACCOUNTANT/SHOP_OWNER select an order, call its delivery-invoices
endpoint, select an ISSUED invoice, load delivery-candidates and create the delivery draft.
The order itself must remain PROCESSING; ISSUED is the invoice status, not an order status.
Order-based compatibility endpoints resolve only an exactly-one ISSUED invoice (drafts are ignored).
Creation and first confirmation both validate the invoice status; a DRAFT or cancelled invoice is rejected.
Invoice state does not change when a delivery is created, confirmed, assigned or completed.
No migration is required for this filter/validation change.

## Serial-line visibility — 2026-11-14
Candidate items include serialTracking from Catalog. Serial-managed lines are shown for context
but cannot be selected by the delivery form. Explicit create requests with such a line return
FUL_005/409 with an actionable serial-support message, rather than generic DB_001.
This does not implement serial dispatch or change Catalog tracking flags; authoritative per-unit
inventory selection/consumption remains required before supporting those lines.

## Printable goods issue note — 2026-11-14

GET `/api/v1/fulfillment/deliveries/{id}/issue-note.pdf` returns an attachment with
`application/pdf`, `Cache-Control: no-store` and an embedded Vietnamese font.
ACCOUNTANT/SHOP_OWNER may download any confirmed issue note; SHIPPER may download
only a delivery currently assigned to that user, enforced through DeliveryService.detail.
Draft/cancelled or unconfirmed deliveries return FUL_001 (409).
The PDF includes order/invoice codes, delivery batch, warehouse, confirmed time,
recipient, shipper, issued line quantities, planned COD and signature spaces.
It does not issue an invoice, change stock, or acknowledge payment.
The current model reuses Delivery as the issue-note record; no new table or migration.
Frontend `/app/issue-notes` lists all delivery-backed notes, including drafts and
cancelled drafts, with status/date filters and confirmed time. Date filters refer
to the scheduled delivery date. Download is available from confirmed note details.

## Reservation guidance before confirmation — 2026-11-14

Confirmation additionally reports FUL_006 (409) when inventory has reserved units
and the current delivery line's own recorded WH_MAIN hold plus free stock cannot
cover its quantity. This is diagnostic guidance to the existing Cọc & hàng giữ
workspace, not authorization to adopt or release aggregate holds.
Sales/owner keep existing reservation-write permissions; accountant can review and
coordinate. Retry uses the same delivery. No hold is created by this check.
Owned holds and unreserved free-stock dispatch remain supported. Inventory's locked
validation remains authoritative for concurrent stock changes and integrity failures.
No Order, Inventory or reservation implementation changes and no migration required.
