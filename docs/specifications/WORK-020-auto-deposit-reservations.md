# WORK-020 — Automatic stock holding after verified deposit

## Metadata
- Owning module: sales (finance owns verification; inventory owns holds).
- Risk: L — money, inventory, concurrency and additive persistence.
- Scope/design authorization: user confirmed SRS UC29 flow on 2026-11-07 and explicitly requested BE implementation using the SCRUM19 verification API. Earlier manual synchronization preference is superseded by that direction.
- Design Review: APPROVED for the user-confirmed flow and existing boundaries; technical recovery choices below implement that authorized scope. No approval is claimed for other draft UC29/UC36 capabilities.

## 1. Goal and scope
After staff confirms selection/warehouse on an eligible order, existing UC23 receipts and UC70 verification fund automatic holding when the existing Finance contract reports depositSatisfied=true. No new payment verification route or replacement of finance code. No production enablement of demo adapters. Cancellation/refunds, delivery scheduling and legacy hold ownership migration remain outside this change.

## 2. Actors and security
- Existing owner/admin or assigned SALES_STAFF authorizes selection and manual recovery.
- Existing UC70 verify/reject routes remain ACCOUNTANT-only.
- Background allocation acts on an already authorized persisted selection, with its original actor recorded in inventory history. No request actor or prices are trusted from clients.
- GET is read-only; no global catalog product state is changed.

## 3. Business rules
- Latest user decision: Finance OrderReceivableApi supplies the agreed deposit and depositSatisfied. UC29 does not recalculate a selection-based 20% threshold.
- Only VERIFIED receipts count. Pending/rejected receipts never trigger funding.
- When full funding arrives via existing payment status update, enqueue allocation in that same transaction; process after commit.
- Hold only selected quantities and chosen warehouse, preserve physical stock and record HELD/WAITING quantities. Partial stock does not reduce required deposit.
- Verified money remains committed if allocation fails. Queue retains a stable retry ID; operations never duplicate receipts or holds.
- Creating/converting an order with reserveStockAfterDeposit=true defers legacy stock checks/holds until UC29. Omitted/false retains legacy behavior.

## 4. Data and migration
V20 adds warehouse_code to sales_reservation_requests and sales_reservation_allocations (sales-owned durable work items with UUID IDs/order references, pending/done/failed status, attempts, retry timestamp, safe error code). Existing warehouse is inferred only from the recorded original REQUEST operation; requests without that evidence are left unset and require manual review. Existing migration files/history and existing inventory balances are not rewritten. No application DB migration is executed by the agent.

## 5. Contracts
- Existing POST /finance/payment-verifications/{paymentId}/verify remains the only bank verification action.
- Reservation authorization is unchanged. Response nests existing OrderReceivableView as payment; financial aliases and hardcoded depositPercent are removed. Holding/allocation fields remain.
- POST synchronize remains authorized manual recovery/refill using existing keys and immutable selection; ordinary successful verification no longer needs it.
- POST /sales/orders/{orderId}/stock-reservations/allocations/{allocationId}/retry authorizes access to that order and retries only its matching saved job. No client actor/warehouse/price is accepted. Response includes allocationId and lineStatuses for each selected order item.
- Reuse existing Finance OrderReceivableApi; remove redundant OrderVerifiedPaymentApi/service and UC29 paid_amount projection. No Finance implementation changes.
- Sales internal payment-update event carries eventId, orderId, occurredAt; no cross-module implementation imports. Event is emitted whenever existing SalesOrderApi.updatePaymentStatus records a non-UNPAID status, even if the enum value is unchanged.

## 6. Transactions and recovery
- Payment transaction: existing order lock, verified receipt/payment status, durable allocation job all commit together. Runtime event alone is not the durable queue.
- AFTER_COMMIT listener attempts work in REQUIRES_NEW. Failed stock transaction rolls back every affected line, separately records safe failure, leaves receipt verified.
- Scheduled retry scans due pending/failed jobs after restart; stable job ID generates stable per-line inventory operation IDs. Order lock precedes job/stock locks. Different workers cannot allocate the same job concurrently.
- No new queue job for ordinary orders without a saved selection. Existing stock reconciliation protection remains active.
- Successful partial allocation is completed work; replenishment can use explicit synchronize. No automatic release/dispatch is introduced.

## 7. Acceptance criteria
- Finance agreed deposit is displayed; pending/rejected/insufficient deposits never hold stock. No agreed deposit must not invent a threshold.
- Verify through actual SCRUM19 HTTP endpoint auto-holds without synchronize.
- Cash VERIFIED receipts also trigger holding; receipt retry does not duplicate work.
- Partial stock, concurrent jobs/retries and wrong-role verification are safe.
- Allocation failure/restart recovery never reverses verification, respects order eligibility/snapshot, and reports error separately from deposit status.
- No selection or cancelled order cannot be automatically allocated.
- New opted-in order creation defers holds; existing requests retain behavior.
- PostgreSQL migration, endpoint integration and existing financial/reservation regressions are verified.
