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

UC-23 and UC-70 create and verify one immutable incoming receipt. UC-63 records how that received amount settles one or more Store invoices of the same order after their external electronic-invoice integration succeeds, allowing UC-24 to show the correct paid and outstanding amount per invoice without changing the total order exposure.

### In Scope

- Manual Accountant allocation of a verified receipt to one or more eligible Store invoices that have an electronic-invoice provider ID and signing time.
- Allocation persistence, invoice-target/read APIs, validation, concurrency control, audit event, and UC-24 read-model integration.
- Additive public Sales query for electronically issued Store invoices of one order.

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
2. A target must have `invoice_type = STORE`, internal status `ISSUED` or `DISPATCHED`, non-blank `provider_invoice_id`, non-null `signed_at`, and belong to the receipt's order and customer. There is no local electronic-invoice status column.
3. The active allocation total for one receipt cannot exceed its receipt amount.
4. The active allocation total for one invoice cannot exceed its total amount.
5. A receipt linked to any Store invoice remains an order-level receipt until UC-63 allocates it. A receipt directly linked to a historical non-Store electronic invoice remains a legacy direct payment and cannot be allocated again.
6. The allocation does not create, edit, delete, or re-verify a transaction.
7. UC-24 subtracts allocation from residual unallocated receipts and adds allocation to the target invoice's verified paid amount. The total customer exposure therefore changes exactly once.

## 4. Data and Migration Decisions

- Finance owns `payment_invoice_allocations`.
- The table uses scalar `transaction_id`, `order_id`, and `invoice_id`; it does not use JPA associations across modules.
- V31 is additive: primary key, strictly positive amount check, `ACTIVE` status, timestamps, audit actor, indexes, and PostgreSQL foreign keys where the existing schema permits.
- Existing rows are not backfilled. Existing receipts remain unallocated until an Accountant allocates them.
- Aggregate amount limits are enforced under locks in the service, not by a row-level SQL check.

## 5. Contracts

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| `GET /api/v1/finance/receipts/{receiptId}/allocation-targets` | Accountant | receipt UUID path | receipt residual and eligible invoice balances | receipt/order not found, invalid receipt state |
| `GET /api/v1/finance/receipts/{receiptId}/allocations` | Accountant | receipt UUID path | allocation history | receipt not found |
| `POST /api/v1/finance/receipts/{receiptId}/allocations` | Accountant | non-empty invoice lines; positive amounts; optional reason | saved allocations and residual receipt amount | invalid receipt, invalid target, amount exceeded, conflict |

### Inter-Module Java API

| Provider | Consumer | Method/DTO | Transaction | Errors/Compatibility |
|---|---|---|---|---|
| Sales `SalesReceivableApi` | Finance | `findElectronicallyIssuedStoreInvoicesByOrderId(UUID)` returning `SalesInvoiceDebtDto` | read-only | returns empty list; provider ID + signing time prove successful integration |
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
| AC-01 | Verified order-level receipt allocates to one electronically issued Store invoice | Service test | invoice paid increases; residual receipt decreases |
| AC-02 | One receipt allocates to two invoices | Service test | two rows saved; total does not exceed receipt |
| AC-03 | Pending receipt, Store invoice without successful external integration, different-order/customer, or over-balance target is rejected | Service/controller test | no allocation saved; defined business error |
| AC-04 | UC-24 projection consumes allocations once | Service test | per-invoice balance changes; total exposure does not double count |
| AC-05 | Concurrent allocation/verification is safe | PostgreSQL integration test | no allocation exceeds receipt/invoice limit |
| AC-06 | Allocation is Accountant-only and audited | Security/manual test | unauthorized request is forbidden; audit record is written after commit |

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| UC-25 trigger after electronic invoice is issued | UC-25 owner and Finance owner | Automatic advance allocation | Open; out of current implementation scope |
| Re-point/reversal execution after UC-26 tax replacement | UC-26 and UC-63 owners | UC-26 integration | Open; current schema reserves allocation lifecycle fields but does not expose reversal endpoint |
