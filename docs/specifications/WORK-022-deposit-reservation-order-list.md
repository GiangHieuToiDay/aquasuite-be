# WORK-022 — Danh sách đơn cho Cọc và giữ hàng

## Metadata
- Owning module: sales
- Risk: L — persisted reservation intent, additive schema and HTTP filtering contract.
- Design Review: APPROVED — owner authorized implementation on 2026-11-08, requiring preservation of other flows.
- Decision owner: repository owner.
- Functional scope requested by owner: only orders requiring deposit and stock holding appear on this page.

## 1. Goal and scope
Filter orders at the database before pagination. Keep the ordinary orders list unchanged.
Reuse existing Finance payment information and verification; no transaction-code input or second receipt is required to inspect an order's deposit.
Frontend is a separate repository and is outside this checkout's authorized scope.

## 2. Actors and security
Preserve current order-list and stock-reservation authorization. No new roles or payment verification powers.

## 3. Business rules
- Persist the existing create/convert request flag reserveStockAfterDeposit on Order.
- A matching order needs explicit stock-holding intent and a positive required deposit.
- Holding intent: persisted flag OR a saved sales_reservation_requests selection for that order.
- A required deposit uses the existing Finance semantics: positive orders.deposit_amount, otherwise the amount of the first payment milestone (same ordering as the existing Sales payment API). Do not infer it from project type, payment status, or deposit_percent alone.
- Pending deposits remain visible; verified money is not required for list membership.
- Cancelled orders are excluded. The initial filter targets CONFIRMED/PROCESSING orders supported by UC29.
- Full-hold history/completion tabs are deferred: do not hide a completed allocation merely because its job status is DONE, since DONE may still mean partial stock.

## 4. Data and migration
- Add orders.reserve_stock_after_deposit boolean NOT NULL DEFAULT false in a new migration; never edit existing migrations.
- Store the flag when creating or converting an order.
- Preserve existing orders and holds. Do not guess historical intent from PROJECT or payments. Legacy orders with a saved reservation request remain discoverable through that explicit evidence.
- Old opted-in orders without a saved selection have no reliable persisted evidence and need owner-directed reconciliation; do not mass-mark them.

## 5. Contracts
- GET /api/v1/sales/orders?depositReservationOnly=true activates the filter before pagination and count.
- Omitted/false preserves ordinary list behavior and existing other filters.
- Existing create/convert reserveStockAfterDeposit field keeps its meaning; it becomes durable.
- GET /api/v1/sales/orders/{orderId}/stock-reservations already returns Finance payment details by order ID, without a paymentId supplied by the caller. Reuse it.
- No changes to finance APIs, payment status, receipt creation, automatic allocation or stock mutation rules.

## 6. Transaction and failure safety
Persist intent in the existing order creation transaction. List/get remain read-only. No reservation or payment side effect occurs during filtering.

## 7. Acceptance criteria and verification
- Opted-in order with a deposit appears even before selecting reservation lines.
- Saved legacy selection with a deposit appears; unrelated paid/project orders do not.
- No deposit, no holding intent, cancelled/ineligible orders are excluded.
- Pagination total and pages reflect only matching rows; existing filters still compose.
- Create/convert persist the flag; ordinary orders retain false and their legacy holding behavior.
- Tests cover positive/zero/absent deposit and milestone fallback consistent with existing Finance precedence.
- Migration applies on isolated PostgreSQL and preserves old order data.

## 8. Required design decision
Owner approval required for the new persisted flag, additive list filter and historical-data policy above. No approval is claimed from the previous automatic-allocation design.

## 9. Owner revision: quotation deposit and one warehouse (2026-11-08)
Owner clarified that depositPercent (e.g. 20) on the quotation triggers the deposit/hold workflow,
and the system has one warehouse. On conversion, a positive depositPercent automatically sets
reserveStockAfterDeposit=true and records all converted order lines for WH_MAIN, the existing
Inventory main-warehouse code, with the authorized staff actor. No extra checkbox/selection is needed.
This supersedes the explicit-request-only requirement for percentage-deposit quotation conversions.
Existing Finance depositAmount calculation/precedence, verification authority, funding checks,
durable queue and partial-stock behavior stay unchanged. Registration writes no stock or receipts.
No-deposit conversions and direct retail creation keep their existing behavior. No historical backfill.
Design authorization: owner explicitly requested this rule and confirmed depositPercent and single warehouse.

## 10. Owner revision: management displays funded 20% orders only
Owner requested depositPercent=20 AND verified deposit for the management page.
For depositReservationOnly=true, candidates are CONFIRMED/PROCESSING orders with exactly
deposit_percent=20; historical intent flags/selections are no longer a prerequisite for visibility.
Reuse OrderReceivableApi.getOrderReceivable(orderId).depositSatisfied to require sufficient
VERIFIED receipts of that order. PENDING/REJECTED or insufficient money does not qualify.
Filter by the resulting IDs before the final database page/count; ordinary lists are unchanged.
This supersedes the earlier rule showing unfunded requests and arbitrary positive percentages.
No Finance/Inventory implementation, verification authority, allocation or historical data is modified.
Current public Finance API is per-order, so management filtering reads each candidate's Finance view;
introduce a batch public query if candidate volume makes this costly. No direct Finance table reads.

## 11. Latest owner revision: display all required deposits with status
Owner superseded section 10: show all orders requiring a deposit, including orders not yet
sufficiently funded, and label insufficient funding. Percentage is not limited to 20.
Eligible CONFIRMED/PROCESSING candidates have a positive deposit percentage/amount or payment
milestone; Finance remains the authority for a positive depositRequired and verified amounts.
The management response includes existing public Finance `payment` and `depositStatus`:
SATISFIED means Đủ cọc; AWAITING_DEPOSIT means Chưa đủ cọc, including pending verification.
Optional fields are omitted on ordinary list responses, preserving those clients' JSON shape.
Determine qualifying IDs before final pagination/count, retain Finance views for response mapping.
No payment status, receipt, verification, allocation or stock mutations occur in this list query.
