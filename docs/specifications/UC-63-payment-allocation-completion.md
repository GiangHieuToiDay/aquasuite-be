# UC-63 — Payment Allocation Completion

- **Work ID**: FEATURE-UC63-ALLOCATION-UI-INTEGRATION
- **Owning Module**: finance
- **Risk Reasons**: money, concurrency, public contract, cross-module
- **Design Review**: APPROVED by Product Owner on 17/11/2026

## Goal and Scope

Expose a paginated queue of verified receipts with unallocated balances and let the Accountant allocate that balance to issued, electronically integrated invoices of the same order. This includes retail Store invoices and Project delivery-batch invoices. The receipt remains immutable; allocation rows are the sole settlement evidence.

In scope: read queue, target/allocate/history APIs, frontend queue/workspace, Accountant reversal, automatic allocation, and UC-26 reconciliation to a replacement invoice. UC-64 still owns creation and verification of the outgoing refund transaction; UC-63 only consumes its verified result so refunded money cannot be allocated again.

## Security and Business Rules

Only `ACCOUNTANT` may view or mutate UC-63 manually. A queue row is a positive `RECEIPT` in `VERIFIED` status with an order and a positive remaining amount after ACTIVE allocations and verified-refund settlements. Allocation validates same order/customer, integrated issued invoice, unique invoice line, receipt balance and invoice balance. Automatic FIFO allocation runs when a receipt becomes VERIFIED and when an invoice receives its provider invoice ID and signing time. Reversal always requires a reason and never changes the receipt transaction itself.

## HTTP Contract

| Method/Path | Authorization | Result |
|---|---|---|
| `GET /api/v1/finance/receipts/unallocated?view=UNALLOCATED\|ALLOCATED\|ALL` | ACCOUNTANT | Paginated verified-receipt ledger. Defaults to `UNALLOCATED`; `ALLOCATED` shows rows fully consumed by allocations or verified refunds; `ALL` shows both. |
| Existing `GET/POST /api/v1/finance/receipts/{receiptId}/…` | ACCOUNTANT | Targets/history and idempotent allocation batch |
| `POST /api/v1/finance/receipts/{receiptId}/allocations/{allocationId}/reverse` | ACCOUNTANT | Marks an active allocation `REVERSED`, stores the mandatory reason, and returns the non-refunded balance to the manual queue |

## Concurrency and Failure Safety

Allocation locks the Sales order before the receipt and persists all allocation rows in one transaction. Client provides `allocationRequestId`; a replay with identical payload returns the same result, while a changed replay conflicts. Queue data is advisory; server validation remains authoritative.

## UC-26 / UC-64 Reconciliation

When UC-26 records the tax result for a full cancellation or a UC-21 replacement order, Finance reverses active allocations on the old order and creates `REPOINTED` rows to the replacement invoice. An Issued_Undelivered partial adjustment keeps the original order/invoice settlement unchanged. If a cancellation or replacement cannot absorb all verified money, Finance requires a `CUSTOMER_REFUND` transaction already verified by UC-64 and records the excess in `payment_receipt_refund_settlements`. The queue and UC-24 deduct that settlement, so a refund cannot silently return as money available for allocation.
