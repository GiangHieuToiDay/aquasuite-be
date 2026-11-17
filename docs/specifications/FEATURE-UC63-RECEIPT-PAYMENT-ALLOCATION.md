# UC-63 — Manage Receipts and Payment Allocation

## Metadata

- **Work ID**: `UC63-RECEIPT-PAYMENT-ALLOCATION`
- **Feature / Use Case**: UC-63 / FE-14 — Manage Receipts and Payment Allocation
- **Version**: `1.0.0`
- **Owner**: Nguyen Quang Hao
- **Owning Module**: `finance`
- **Risk Reasons**: money, concurrency, data migration, public contract, cross-module integration
- **Design Review**: `APPROVED`
- **Decision Owner**: Project Owner

## 1. Goal and Scope

### Problem and Desired Outcome

UC-23 and UC-70 create and verify one immutable incoming receipt. UC-63 records how that received amount settles one or more invoices of the same order after their external electronic-invoice integration succeeds, allowing UC-24 to show the correct paid and outstanding amount per invoice without changing the total order exposure. Both Store invoices and Project delivery-batch invoices are supported.

### In Scope

- Manual Accountant allocation and bounded automatic FIFO application of verified receipts to eligible invoices that have an electronic-invoice provider ID and signing time.
- Allocation persistence, invoice-target/read APIs, validation, concurrency control, audit event, and UC-24 read-model integration.
- Additive public Sales query for electronically integrated invoices of one order.

### Out of Scope

- Creating, verifying, rejecting, reversing, or refunding receipt transactions.
- Issuing, replacing, adjusting, or cancelling an electronic invoice.
- Automatic invocation at invoice issue time until UC-25 supplies its approved contract/event.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role | Data Restrictions |
|---|---|---|---|
| Accountant | List allocation targets, allocate verified receipts, view allocation history | `ACCOUNTANT` | Only receipts and invoices of the same customer/order |

- The actor is taken from the authenticated session; no actor ID is accepted from HTTP input.
- The API never returns proof content, credentials, or payment instrumentation data.

## 3. Business Rules

1. Only `RECEIPT` transactions in `VERIFIED` status and with a non-null `order_id` are allocatable.
2. A target must have internal status `ISSUED` or `DISPATCHED`, non-blank `provider_invoice_id`, non-null `signed_at`, and belong to the receipt's order and customer. The target may be `STORE` or `PROJECT`; there is no local electronic-invoice status column.
3. The active allocation total for one receipt cannot exceed its receipt amount.
4. The active allocation total for one invoice cannot exceed its total amount.
5. A receipt's `invoice_id` is an origin reference only; it remains an order-level receipt until UC-63 writes allocation rows.
6. The allocation does not create, edit, delete, or re-verify a transaction.
7. UC-24 subtracts allocation from residual unallocated receipts and adds allocation to the target invoice's verified paid amount. The total customer exposure therefore changes exactly once.
8. A one-stage order automatically applies every verified receipt FIFO. A staged-delivery order automatically applies only its earliest verified receipts up to the immutable `orders.deposit_amount`; all money above that cap remains unallocated for an Accountant to allocate manually.
9. `orders.is_staged_delivery` is a Sales snapshot created from quotation batches or when a second invoice batch is created. It prevents UC-63 from inferring settlement rules from mutable invoice state.
10. An Accountant correction reverses the old allocation with a mandatory reason before a new manual allocation is created. A replacement electronic invoice requires a `REVERSED` source row and a same-value `REPOINTED` target row; if the replacement has a lower total, its excess cannot become reusable money until UC-64 has verified the corresponding refund.

## 4. Data and Migration Decisions

- Finance owns `payment_invoice_allocations` and `payment_receipt_refund_settlements`.
- The table uses scalar `transaction_id`, `order_id`, and `invoice_id`; it does not use JPA associations across modules.
- V40 is additive: primary key, strictly positive amount check, `ACTIVE` status, timestamps, audit actor, indexes, and PostgreSQL foreign keys where the existing schema permits.
- V46 adds `orders.is_staged_delivery` and marks historical orders that already have more than one invoice as staged. Existing receipt allocations are unchanged.
- V47 records how a UC-64-verified customer refund consumes an original receipt balance. It is intentionally separate from invoice allocation because a refund has no target invoice.
- Aggregate amount limits are enforced under locks in the service, not by a row-level SQL check.

## 5. Contracts

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| `GET /api/v1/finance/receipts/{receiptId}/allocation-targets` | Accountant | receipt UUID path | receipt residual and eligible invoice balances | receipt/order not found, invalid receipt state |
| `GET /api/v1/finance/receipts/{receiptId}/allocations` | Accountant | receipt UUID path | allocation history | receipt not found |
| `POST /api/v1/finance/receipts/{receiptId}/allocations` | Accountant | non-empty invoice lines; positive amounts; optional reason | saved allocations and residual receipt amount | invalid receipt, invalid target, amount exceeded, conflict |
| `POST /api/v1/finance/receipts/{receiptId}/allocations/{allocationId}/reverse` | Accountant | non-blank correction reason | row marked `REVERSED`; balance returns only to manual queue | allocation/receipt missing, already reversed, invalid receipt |

### Inter-Module Java API

| Provider | Consumer | Method/DTO | Transaction | Errors/Compatibility |
|---|---|---|---|---|
| Sales `SalesReceivableApi` | Finance | `findElectronicallyIntegratedInvoicesByOrderId(UUID)` returning `SalesInvoiceDebtDto` | read-only | returns empty list; provider ID + signing time prove successful integration |
| Sales `SalesOrderApi` | Finance | existing `lockOrderPaymentInfo(UUID)` | mandatory caller transaction | order missing/not payable follows existing contract |

### Domain Event

| Event | Publisher/Consumers | Payload | Timing | Idempotency/Failure Handling |
|---|---|---|---|---|
| Existing `CustomerDebtAuditEvent` | Finance to Audit | allocation IDs, receipt ID, action, before/after values, actor, reason | after commit | `eventId`; existing Audit listener ignores duplicate and logs persistence failures |

## 6. Concurrency, Transaction and Failure Safety

- The allocation transaction locks its Sales order first through `SalesOrderApi.lockOrderPaymentInfo`, then locks the receipt with `TransactionRepository.findByIdForUpdate`.
- Allocation totals are recalculated after locks and before `saveAll`.
- The transaction rolls back fully if target validation or persistence fails.
- The client supplies `allocationRequestId` (UUID) for a batch. Retrying the exact same receipt, request ID, invoice lines, amounts and reason returns the saved batch; reusing that request ID with different data returns `ALC_011`.
- The database unique key `(transaction_id, allocation_request_id, invoice_id)` is a second idempotency guard. It does not prevent a later, different batch from allocating the remaining receipt balance.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Verified order-level receipt allocates to one electronically integrated invoice | Service test | invoice paid increases; residual receipt decreases |
| AC-02 | One receipt allocates to two invoices | Service test | two rows saved; total does not exceed receipt |
| AC-03 | Pending receipt, invoice without successful external integration, different-order/customer, or over-balance target is rejected | Service/controller test | no allocation saved; defined business error |
| AC-04 | UC-24 projection consumes allocations once | Service test | per-invoice balance changes; total exposure does not double count |
| AC-05 | Concurrent allocation/verification is safe | PostgreSQL integration test | no allocation exceeds receipt/invoice limit |
| AC-06 | Allocation is Accountant-only and audited | Security/manual test | unauthorized request is forbidden; audit record is written after commit |
| AC-07 | One-stage order automatically allocates all verified money FIFO | Service test | allocation rows consume eligible invoices until either the receipt or invoice balance is exhausted |
| AC-08 | Staged order automatically allocates no more than its deposit | Service test | earliest verified receipts contribute at most `deposit_amount`; excess remains in the Accountant queue |

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| Electronic-invoice integration trigger | UC-25 owner and Finance owner | Automatic allocation | Resolved: Accountant persists provider ID + signing time through Sales; its event asks Finance to re-evaluate verified receipts under the one-stage/deposit rule. |
| Re-point/reversal execution after UC-26 tax replacement | UC-26 and UC-63 owners | UC-26 integration | Resolved: UC-26 calls Finance's public `PaymentAllocationAdjustmentApi`; Finance reverses old rows, writes `REPOINTED` rows to the replacement invoice, and requires a UC-64 VERIFIED refund before recording any unreused excess in V47. |
