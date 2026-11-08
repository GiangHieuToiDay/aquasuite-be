# UC-24 — Manage Customer Debt and Receivables

## Metadata

- **Work ID**: `UC-24-CUSTOMER-DEBT-RECEIVABLES`
- **Feature / Use Case**: UC-24 / FE-14 — Manage Customer Debt and Receivables
- **Version**: `0.1.0`
- **Owning Module**: `finance` (with public CRM, Sales, and Audit contracts)
- **Risk Reasons**: money, credit authorization, data migration, concurrency, public contract, and cross-module integration
- **Design Review**: `APPROVED`
- **Decision Owner**: Project Owner

## 1. Goal and Scope

### Problem and Desired Outcome

Internal staff need an explainable, current view of customer receivables without creating a duplicated receivables balance table. UC-24 derives debt from orders, issued invoices, verified receipts, and payment-allocation results. It controls credit before a new counter sale, sends operational reminders, exports an internal PDF statement, and lets a Shop Owner block or restore a customer after the blacklist process.

### In Scope

- Debt dashboard: statistics, pagination, search by customer code/name/company/phone, customer-type and debt-status filters.
- Customer debt detail: profile, addresses, projects, order/invoice/payment projection, and reminder history from `audit_logs`.
- Credit check for a counter order and exactly-one-order Shop Owner override.
- Derived overdue, credit-blocked, and blacklist status.
- Email reminder and simulated/manual phone-call result; SMS is a future adapter.
- PDF statement download and optional email delivery.
- Shop Owner decision to set `customers.status` to `BLOCKED`, and manual restore to `ACTIVE`.
- Forward-only migration that aligns the running CRM customer/invoice model with the supplied `Database.docx` fields needed by this use case.

### Out of Scope

- Creating or editing credit profiles: UC-49.
- Recording or verifying payments: UC-23 and UC-70.
- Allocating a general order payment across invoices: UC-63.
- Formal debt reconciliation/confirmation, provisions, reporting dashboards, or scheduled collection: UC-65, UC-68, and UC-53.
- A self-service customer debt portal.
- A live SMS provider integration, automatic reminder scheduler, or retry/outbox worker.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role | Data Restrictions |
|---|---|---|---|
| Sales Staff | View debt, run a counter-order credit check, send email reminder, record a reminder call | `SALES_STAFF` | Internal customer financial data only |
| Accountant | View debt/detail and export/download statement | `ACCOUNTANT` | Cannot send reminder/call, override, block, or restore |
| Shop Owner | All UC-24 operations | `SHOP_OWNER` | May override one order; may block/restore a customer |

- Endpoints are authenticated. The controller uses role checks matching this table; no Customer/Guest route is exposed.
- A PDF, email payload, audit JSON, and logs must not contain credentials, payment proof URLs, or unmasked secrets.
- Customer status is persisted only as `ACTIVE`, `INACTIVE`, or `BLOCKED`. `BLOCKED` is the business state called “Banned” in the SRS: it blocks every new order, including immediate-payment orders.
- `WITHIN_TERM`, `OVERDUE`, `CREDIT_BLOCKED`, and `BLACKLISTED` are derived response states; they are not written to `customers.status`.

## 3. Business Rules

1. Customer total outstanding is derived from non-cancelled orders and verified receipts; invoice totals are detail records and are never added on top of order totals.
2. A debt due date is `sales_invoices.issue_date + customers.credit_term_days`. The term is 1 through 30 days; it is not globally fixed at 30 days.
3. `OVERDUE` exists when an `ISSUED` invoice has a positive outstanding amount and its due date is before today. Invoice age is not an overdue bucket.
4. `BLACKLISTED` exists when an `ISSUED` invoice remains outstanding more than 60 calendar days after `issue_date`. It cannot be overridden for credit.
5. A credit order is only available for a `CONTRACTOR` or `PROJECT_CUSTOMER` with a positive credit limit and valid credit term, and only on the in-store counter channel.
6. A credit check rejects when the customer is `BLOCKED`, blacklisted, has overdue debt, or the proposed amount exceeds available credit. A credit-blocked customer may still use immediate payment unless `customers.status = BLOCKED`.
7. A Shop Owner may approve an override only for a non-blacklisted, non-`BLOCKED` customer and one identified order. The reason is mandatory. The override is represented by an audit record and never increases the stored credit limit.
8. A direct debt payment must be linked to its invoice at UC-23: the verified transaction has `invoice_id`, and its invoice/order/customer identities must agree.
9. A verified receipt linked only to an order is an unallocated order receipt. It may contribute to order-level totals, but it must not reduce a specific invoice, overdue amount, or blacklist calculation until UC-63 creates an allocation result. The detail response exposes it separately instead of guessing FIFO, LIFO, or proportional allocation.
10. Before dispatching a reminder, re-read the debt. If outstanding is zero, do not send it. An upcoming reminder is friendly; overdue/final reminders use escalation wording.
11. Reminder dispatch, phone-call result, override, block/restore, export, and email delivery are append-only audit actions. No `debt_reminders` or `credit_overrides` table is created.
12. Until an SMS provider is configured, SMS is returned and audited as `NOT_CONFIGURED`; the application never claims that an SMS was sent.
13. A statement is an internal PDF snapshot. It contains opening balance, issued-invoice debit movements, verified-payment credit movements, closing balance, due-date/overdue information, and unallocated verified order receipts. It is not a formal debt confirmation.

### State Semantics

| State Type | Value | Meaning |
|---|---|---|
| Persisted customer state | `ACTIVE` | Customer may transact subject to normal credit rules |
| Persisted customer state | `INACTIVE` | CRM deactivation; outside UC-24 lifecycle |
| Persisted customer state | `BLOCKED` | Shop Owner has prohibited all new orders |
| Derived debt state | `WITHIN_TERM` | Debt exists but no due invoice has passed its due date |
| Derived debt state | `OVERDUE` | At least one issued invoice is overdue |
| Derived debt state | `CREDIT_BLOCKED` | New credit sale fails credit rule; immediate payment remains possible |
| Derived debt state | `BLACKLISTED` | At least one issued, unpaid invoice is older than 60 days |

## 4. Data and Migration Decisions

- `customers`, `orders`, `sales_invoices`, `transactions`, and `audit_logs` are the source tables. No materialized debt balance or receivables table is added.
- A new forward-only Flyway migration adds the missing supplied-database fields to `customers`:
  - `projects TEXT[]`.
  - `credit_limit NUMERIC(15,2) NOT NULL DEFAULT 0`, with non-negative check.
  - `credit_term_days INTEGER`, nullable only when no credit exists; values 1–30 when `credit_limit > 0`.
- The supplied customer schema declares `address TEXT[]`, while the running baseline has `address TEXT` and the normalized `customer_addresses` table. The migration converts a non-null legacy scalar address into a one-element array without dropping `customer_addresses`; UC-24 exposes the migrated customer address array in its debt-profile projection. The normalized address table remains available to the CRM delivery-address flows.
- The migration adds the supplied invoice fields required by the UI: `invoice_symbol`, `signed_at`, `provider_code`, `provider_invoice_id`, and `original_invoice_id`. It does not recreate invoice tables.
- Existing customers receive `credit_limit = 0`; they are not eligible for credit until UC-49 configures a valid profile. Existing `address` values are preserved during conversion. Existing projects are initialized empty/null.
- `BLOCKED` already exists in the baseline customer-status check and requires no new status value.
- Cross-module references in Java remain scalar UUIDs. No cross-module JPA association is introduced.

## 5. Contracts

### HTTP API

All endpoints return `ApiResponse<T>` except PDF download, which returns `application/pdf` with a safe attachment filename.

| Method/Path | Authorization | Purpose |
|---|---|---|
| `GET /api/v1/finance/customer-debts` | Sales Staff, Accountant, Shop Owner | Paginated dashboard list/filter |
| `GET /api/v1/finance/customer-debts/stats` | Sales Staff, Accountant, Shop Owner | Dashboard cards |
| `GET /api/v1/finance/customer-debts/{customerId}` | Sales Staff, Accountant, Shop Owner | Detail projection and audit reminder history |
| `POST /api/v1/finance/customer-debts/{customerId}/credit-check` | Sales Staff, Shop Owner | Counter-order credit decision |
| `POST /api/v1/finance/customer-debts/orders/{orderId}/credit-override` | Shop Owner | One-order override with mandatory reason |
| `POST /api/v1/finance/customer-debts/{customerId}/reminders` | Sales Staff, Shop Owner | Email/SMS reminder request |
| `POST /api/v1/finance/customer-debts/{customerId}/reminder-calls` | Sales Staff, Shop Owner | Record a phone-call result |
| `GET /api/v1/finance/customer-debts/{customerId}/statement` | Accountant, Shop Owner | Download statement PDF for a period |
| `POST /api/v1/finance/customer-debts/{customerId}/statement/email` | Accountant, Shop Owner | Send statement PDF by email |
| `GET /api/v1/finance/customer-debts/{customerId}/blacklist-status` | Sales Staff, Accountant, Shop Owner | Derived blacklist explanation |
| `PATCH /api/v1/finance/customer-debts/{customerId}/block` | Shop Owner | Persist `customers.status = BLOCKED` |
| `PATCH /api/v1/finance/customer-debts/{customerId}/restore` | Shop Owner | Restore customer to `ACTIVE` with reason |

`MSG62` remains the success message for a fully configured successful reminder dispatch. `MSG63` remains the credit-block error. The UC-24 blacklist response uses a new finance error code rather than reusing `MSG69`, because `MSG69` is already assigned to UC-31.

### Inter-Module Java API

| Provider | Consumer | Contract |
|---|---|---|
| CRM | Finance | Add a public entity-free debt-profile query and a status-update operation for Shop Owner decisions |
| Sales | Finance | Add a public entity-free order/invoice receivable projection, including issue date, status, and direct/allocation results |
| Finance | Sales / UC-21 | Expose final credit authorization so a preflight check is repeated inside the credit-order write transaction |
| Audit | Finance | Add a public read contract for customer debt history; Finance never imports `audit.internal` |

### Domain Event

Finance publishes one immutable `CustomerDebtAuditEvent` after a successful source transaction. Its action is one of `CREDIT_CHECKED`, `CREDIT_OVERRIDE_APPROVED`, `DEBT_REMINDER_SENT`, `DEBT_REMINDER_FAILED`, `DEBT_CALL_RECORDED`, `STATEMENT_EXPORTED`, `STATEMENT_EMAILED`, `STATEMENT_EMAIL_FAILED`, `CUSTOMER_BLOCKED`, or `CUSTOMER_RESTORED`.

Audit consumes the event after commit in a new transaction, deduplicates by event ID, and persists it in `audit_logs`. Event payloads contain only required business snapshots and never secrets or payment proofs.

## 6. Concurrency, Transaction and Failure Safety

- A dashboard/detail query is read-only and uses a single `asOf` time for all derived status calculations.
- Credit approval is not a reservation. UC-21 must call Finance’s final authorization operation inside its own credit-order write transaction.
- Final credit authorization and override approval serialize by PostgreSQL advisory transaction lock derived from the customer ID/order ID, then re-read current debt and relevant audit actions.
- Audit-only override storage uses the same order lock plus an audit-history check, so two concurrent approvals cannot both approve the same order through this application.
- Direct invoice payment validation rejects mismatched customer/order/invoice identities. UC-24 never writes payment records or allocations.
- Email failure does not alter debt or roll back a successful audit-worthy business operation. It is audited for manual retry. SMS remains `NOT_CONFIGURED` until an approved adapter exists.
- PDF generation occurs in memory for the HTTP response/email attachment; no statement file is persisted unless a later document-storage requirement is approved.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification |
|---|---|---|
| AC-01 | List/stat cards correctly derive totals and filters | Finance service unit tests and PostgreSQL integration tests |
| AC-02 | Detail never double-counts order and invoice totals | Projection integration tests with direct and unallocated receipts |
| AC-03 | Credit policy blocks limit/overdue/blacklist cases and allows eligible counter credit | Unit tests plus final-authorize concurrency integration test |
| AC-04 | Direct invoice payment identity is validated; unallocated receipt is not assigned to an invoice | Finance/Sales contract tests |
| AC-05 | Only Shop Owner can override, block, or restore; override is single-use and audited | MockMvc security and transaction tests |
| AC-06 | Reminder and call outcome are auditable; missing/failed channels are truthful | Unit tests with notification ports |
| AC-07 | Statement is a valid PDF with period snapshots and may be emailed | PDF content test and mail-port test |
| AC-08 | Migration preserves existing customers and makes no existing customer credit-eligible by default | Flyway PostgreSQL integration test |

## 8. Open Decisions

| Decision | Owner | Status |
|---|---|---|
| Use `BLOCKED` as the persisted implementation of the SRS “Banned” decision | Project Owner | RESOLVED |
| Store reminders and overrides solely in `audit_logs` | Project Owner | RESOLVED |
| Direct payment requires `invoice_id`; general payment needs UC-63 allocation for invoice detail | Project Owner | RESOLVED |
| Statement output is PDF; SMS provider is deferred | Project Owner | RESOLVED |
| Design review of this specification and its V4 migration/API scope | Project Owner | APPROVED — 2026-11-01 |
