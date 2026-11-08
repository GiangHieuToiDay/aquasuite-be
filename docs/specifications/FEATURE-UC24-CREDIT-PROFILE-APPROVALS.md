# UC-24 — Customer Credit Profile Approval

## Metadata

- **Work ID**: `FEATURE-UC24-CREDIT-PROFILE-APPROVALS`
- **Feature / Use Case**: UC-24 Customer Debt and Receivables — credit profile approval
- **Version**: `1.0`
- **Owner**: Product Owner
- **Owning Module**: `crm`
- **Risk Reasons**: authorization, customer credit exposure, data migration, public contract, cross-module query, sensitive identity data
- **Design Review**: `APPROVED`
- **Decision Owner**: Product Owner — approved on 2026-11-08

## 1. Goal and Scope

### Problem and Desired Outcome

Replace the POS flow that re-authenticates a Shop Owner with email and password. A Sales Staff member either receives an immediate rule-based credit-profile update or creates a durable request for Shop Owner review. The Shop Owner reviews pending requests from UC-24 debt management. A customer can never receive a credit limit over 500,000,000 VND.

### In Scope

- Durable `customer_credit_profile_requests` owned by CRM.
- Rule-based immediate application and Shop Owner approval/rejection.
- A Shop Owner-only pending-request query/detail/decision API for the UC-24 screen.
- Qualified-order history query owned by Sales and exposed through `sales.api`.
- Audit events for request submission, approval/rejection, and profile changes without leaking identity data.
- Deprecation of the password-based credit-approval grant flow for this use case.

### Out of Scope

- Creating or confirming orders (UC-21/UC-22), payment verification (UC-23/UC-63/UC-70), invoicing, and debt calculation.
- Encryption/key-management infrastructure for identity documents.
- Credit scoring, multi-stage approval, or historical request editing.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role | Data restrictions |
|---|---|---|---|
| Sales Staff | Submit credit profile request; receive immediate outcome or pending status | `SALES_STAFF` | Cannot list or decide pending requests; identity document never returned after submission |
| Shop Owner | Submit and immediately apply own request; list/detail/approve/reject pending requests | `SHOP_OWNER` | Only role allowed to view request identity evidence and make decisions |
| Accountant | Existing receivables read screens only | `ACCOUNTANT` | Cannot access credit approval requests or identity evidence |

- Identity-document number, residential address, and worksite address are stored only in the CRM request record for `INSTALLER`, `CONTRACTOR`, and `PROJECT_CUSTOMER` automatic-credit eligibility. They are never copied into `audit_logs`, events, application logs, list responses, or Sales APIs.
- The legacy IAM password/grant endpoints must not authorize the new workflow. The UI must use the new CRM request endpoint.

## 3. Business Rules

1. A qualified historic order is an order for the same customer whose `order_status = COMPLETED` and `payment_status = PAID`. Cancelled, processing, partially paid, and unpaid orders never count.
2. The requested credit limit is greater than zero and at most 500,000,000 VND. A credit term is 1–30 days.
3. A Sales Staff member may immediately apply a request for a `RETAIL` customer only after at least three qualified orders and only when the requested limit is at most 2,000,000 VND.
4. A Sales Staff member may immediately apply a request for an `INSTALLER`, `CONTRACTOR`, or `PROJECT_CUSTOMER` only after at least three qualified orders, with a limit at most 10,000,000 VND, and only when identity document, residential address, and worksite address are present.
5. A Sales Staff request that fails an immediate-application rule is stored as `PENDING` for Shop Owner review. The customer profile remains unchanged until approval.
6. A Shop Owner submitting a request applies it immediately, subject to the universal 500,000,000 VND and 1–30-day constraints. Evidence remains visible when provided but is not a technical prerequisite for the Owner's direct decision.
7. A customer has at most one `PENDING` request. A duplicate pending submission is rejected rather than overwritten.
8. Approval copies requested limit and term to `customers.credit_limit` and `customers.credit_term_days`. Rejection never changes the active profile.
9. Reducing a current limit or term follows the same request/approval path in v1; it is not granted an undocumented bypass.

| From | Action | To | Conditions |
|---|---|---|---|
| none | Sales submits ineligible request | `PENDING` | One pending request maximum |
| none | Sales submits eligible request | `APPROVED` | Rule 3 or 4; active profile updated atomically |
| none | Shop Owner submits | `APPROVED` | Universal constraints and contractor evidence |
| `PENDING` | Shop Owner approves | `APPROVED` | Locks request/customer and updates active profile atomically |
| `PENDING` | Shop Owner rejects | `REJECTED` | Decision note required; active profile unchanged |

## 4. Data and Migration Decisions

- Add `customer_credit_profile_requests` with scalar `customer_id`, `requester_id`, and `reviewed_by` UUIDs. No cross-module JPA relationship is introduced.
- The table contains requested limit/term, status, reason, contractor evidence, decision note, and timestamps.
- A partial unique index allows only one `PENDING` request per customer.
- V15 extends the existing `chk_customers_credit_profile` constraint with the 500,000,000 VND maximum after first failing safely if existing active profiles exceed that amount.
- Existing `customers.credit_limit` and `credit_term_days` stay the active source of truth. Existing data needs no backfill.

## 5. Contracts

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| `POST /api/v1/crm/customers/{customerId}/credit-profile-requests` | Sales Staff, Shop Owner | limit, term, reason; evidence required for contractor group | applied profile or pending request | invalid profile, duplicate pending, customer inactive |
| `GET /api/v1/crm/credit-profile-requests` | Shop Owner | optional status/page | paginated requests; no raw identity evidence in list | forbidden |
| `GET /api/v1/crm/credit-profile-requests/{requestId}` | Shop Owner | none | full review detail | not found/forbidden |
| `POST /api/v1/crm/credit-profile-requests/{requestId}/approve` | Shop Owner | optional review note | approved request and active profile | invalid state/conflict |
| `POST /api/v1/crm/credit-profile-requests/{requestId}/reject` | Shop Owner | nonblank review note | rejected request | invalid state/conflict |

The old `PATCH /api/v1/crm/customers/{customerId}/credit-profile` / IAM grant pair is retired from the UI and must not remain a Sales Staff bypass.

### Inter-Module Java API

| Provider | Consumer | Method/DTO | Transaction | Errors/Compatibility |
|---|---|---|---|---|
| Sales | CRM | `CustomerOrderHistoryApi.countQualifiedOrders(UUID)` | synchronous read | Returns `long`; Sales owns status interpretation |
| IAM | CRM | existing `IamApi.getCurrentUser()` | synchronous identity read | Used only for current role; no IAM internal imports |

### Domain Event

| Event | Publisher/Consumers | Payload | Timing | Idempotency/Failure Handling |
|---|---|---|---|---|
| `CustomerCreditProfileRequestEvent` | CRM → Audit | event/request/customer/actor IDs, action, requested values, evidence-present flag, decision metadata | publish in source transaction; audit after commit | `eventId`; audit logs failure and does not roll back credit decision |
| existing `CustomerCreditProfileUpdatedEvent` | CRM → Audit | approved profile values; sanitized reason only | publish in source transaction; audit after commit | existing event ID deduplication |

## 6. Concurrency, Transaction and Failure Safety

- Submission checks the unique pending index and maps a duplicate-key race to the business duplicate-pending error.
- Approval locks the request and the customer before confirming `PENDING`, applying the active profile, and changing request state in one transaction.
- A rejected/approved request cannot be decided again.
- Sales order qualification is read at submission time; approval does not retroactively re-run trust rules because Shop Owner is the final authority.
- Audit is after commit and must never expose raw evidence or roll back a completed decision.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Retail trusted customer auto-application | CRM service test | 3 `COMPLETED + PAID`, limit ≤2m updates active profile |
| AC-02 | Contractor-group auto-application | CRM service test | 3 qualified orders, limit ≤10m and all evidence required |
| AC-03 | Ineligible Sales request waits | service/controller test | `PENDING`, active profile unchanged |
| AC-04 | Owner approval/rejection | service/security test | only owner decides; approval is atomic; rejection requires note |
| AC-05 | Cap and duplicate protection | migration/service/integration test | >500m rejected; one pending per customer |
| AC-06 | Audit/privacy | listener test | all actions logged without raw identity value |
| AC-07 | Module boundaries | compile/ArchUnit/import check | CRM imports Sales/IAM `api` only |

## 8. Open Decisions

| Decision | Owner | Status |
|---|---|---|
| Qualified order means `COMPLETED + PAID` | Product Owner | RESOLVED |
| `PROJECT_CUSTOMER` uses contractor-group threshold | Product Owner | RESOLVED |
| One durable request table rather than audit/Redis-only state | Product Owner | RESOLVED |
| Maximum active/requested credit is 500,000,000 VND | Product Owner | RESOLVED |
