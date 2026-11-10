# UC-26 Process Order Modification or Cancellation Requests

## Metadata

- **Work ID**: UC-26
- **Feature / Use Case**: Process Order Modification or Cancellation Requests
- **Version**: 1.0
- **Owner**: AquaSuite Sales Team
- **Owning Module**: `sales`
- **Risk Reasons**: money, inventory, concurrency, data migration, public contract, cross-module
- **Design Review**: APPROVED — 2026-11-03
- **Decision Owner**: Product Owner and Technical Lead

## 1. Goal and Scope

### Problem and Desired Outcome

UC-20 submits a formal request to change or cancel a confirmed, pre-dispatch order. UC-26 lets authorized staff review that request without corrupting order, reservation, payment, or tax-invoice history. Every decision is auditable. The user-selected lines and replacement variants are recorded exactly; staff do not substitute products.

### In Scope

- Search and view requests created by UC-20 or at the sales counter.
- Approve or reject `FULL_CANCELLATION`, `ADDRESS_CHANGE`, and `ITEM_CHANGE` requests for eligible Processing orders.
- Persist requested line removals, quantity reductions, and swaps in the two shared sales tables.
- Apply store-only changes atomically to the order, its reservations, and its Draft store invoice.
- Coordinate the tax-linked path through `PENDING_CANCEL`; manually record a confirmed tax adjustment/replacement result in v1.
- Calculate the difference once as the new total minus Verified payments; UC-23 records incoming difference payments and UC-64 approves refunds.
- Publish auditable events after commit and send the approved-result email without promising a refund time.

### Out of Scope

- Customer request submission endpoint/UI (UC-20), creating or first-confirming a replacement order (UC-21/UC-22), independent reservation management (UC-36), physical delivery (UC-39 to UC-41), returns after acceptance (UC-37), and payment/refund verification (UC-23, UC-64, UC-70).
- Direct integration with an electronic-invoice provider in v1.
- A new receivables, credit-wallet, or refund-approval table.

## 2. Actors and Security

| Actor | Allowed operations | Restriction |
|---|---|---|
| Sales Staff | List/detail; approve address change and unpaid, non-tax cancellation; reject eligible requests; send result email | Cannot approve item change, a partial/fully paid cancellation, a refund, or a tax-linked decision. |
| Shop Owner | List/detail; approve or reject every request type; release reservations in the approved business flow | Cannot mark an external tax invoice or refund as verified. |
| Accountant | List/detail only for `PENDING_CANCEL`; record the internal tax-result reference | Does not approve/reject a request, choose a substitute product, or approve a refund. |
| Customer | Submits through UC-20 only | Has no UC-26 internal endpoint. |

- Every mutation gets the actor ID from `IamApi`; the request payload never supplies it.
- Customer reasons, decision notes, price differences, prior/new line data, and invoice references are audit data. No payment credential or external-provider secret may be stored in the request or audit payload.

## 3. Business Rules

1. A request is valid only for an order in `PROCESSING`. A Pending order is cancelled directly by the customer; a Cancelled, Completed, Dispatched, In_Transit, or Delivered order is outside UC-26.
2. Delivery is checked before payment and tax linkage. Any dispatched/in-transit/delivered delivery blocks approval. Returns after acceptance belong to UC-37; partial acceptance at the door belongs to UC-39 to UC-41.
3. Request types are `FULL_CANCELLATION`, `ADDRESS_CHANGE`, and `ITEM_CHANGE`. Line remove/reduce without a replacement is an `ITEM_CHANGE`, not a separate partial-cancellation type.
4. `PENDING_CANCEL` belongs to `order_modification_requests.request_status`, not to `orders.order_status`. The old order stays `PROCESSING` and is locked from direct mutation until the tax/refund result is final. It becomes `CANCELLED` only after the required tax/refund completion.
5. A store invoice with no tax e-invoice link is Draft and can be changed together with the order. A tax-linked invoice is immutable; UC-26 uses a replacement invoice for a swap/new order or an adjustment invoice for cancellation/eligible adjustment.
6. A fully paid, not-dispatched cancellation requires Shop Owner approval, `PENDING_CANCEL`, a confirmed tax result where applicable, and Accountant-approved refund. A paid swap is blocked; staff may instead process the approved cancellation path and create a new order through UC-21.
7. Price difference is calculated once as `new order total - Verified payments already applied`. A positive amount is an obligation for UC-23 against the updated Draft or replacement invoice. A negative amount creates a Pending refund for UC-64; UC-26 never verifies the payment/refund.
8. For `ISSUED_UNDELIVERED`, stock remains committed/reserved in the warehouse. A pre-dispatch line change may use the tax partial-adjustment path for only removed lines; the original invoice remains valid and is offset by that amount. Physical delivery stays in UC-39 to UC-41.

| From request status | Action | To request status | Conditions |
|---|---|---|---|
| `PENDING_REVIEW` | reject | `REJECTED` | Authorized reviewer supplies decision note. |
| `PENDING_REVIEW` | approve store-only address/item/unpaid cancellation | `APPROVED` | Delivery/payment/invoice checks pass; local order/reservation/invoice update succeeds. |
| `PENDING_REVIEW` | approve tax-linked or refund-dependent action | `PENDING_CANCEL` | Shop Owner approval where required; original order/invoice are locked. |
| `PENDING_CANCEL` | record confirmed tax result and observe verified payment/refund result | `APPROVED` | Required adjustment/replacement result exists and any required UC-23/UC-64 result is final. |
| `PENDING_CANCEL` | tax/refund failure | `PENDING_CANCEL` | No order finalization or refund recording; retry is possible. |

## 4. Data and Migration Decisions

- Add forward-only `V5__add_order_modification_requests.sql`; V1 through V4 remain immutable.
- Add `order_modification_requests`, owned by the `sales` module and shared by UC-20/UC-26. UC-20 will create it through a future caller; UC-26 owns review state transitions and execution fields. UC-26 does not expose the UC-20 submission endpoint/UI.
- Add `order_modification_request_items`, the immutable requested line snapshot for `ITEM_CHANGE`.
- Header stores scalar IDs for request/order/customer/requested-by/reviewed-by/replacement-order/original-invoice/replacement-or-adjustment-invoice; request type/status; customer reason; decision note; tax-result reference; calculated difference; timestamps.
- Item stores scalar IDs for request/original order item/original variant/requested replacement variant; action (`REMOVE`, `REDUCE`, `SWAP`); original/requested quantity and unit-price snapshot.
- PostgreSQL foreign keys and indexes are permitted; JPA entity relationships remain within `sales` only and cross-module references are scalar IDs.
- Enforce one active request per order with a partial unique index for `PENDING_REVIEW` and `PENDING_CANCEL`; index order, status, requester, and request-created time for list/detail screens.

## 5. Contracts

### HTTP API

| Method/Path | Authorization | Success | Errors |
|---|---|---|---|
| `GET /api/v1/order-modification-requests` | Sales Staff, Shop Owner; Accountant sees only pending tax/refund work | Paginated request list | `AUTH_002` |
| `GET /api/v1/order-modification-requests/{requestId}` | Same visibility rule | Request/detail/audit projection | `SYS_001`, `AUTH_002` |
| `POST /api/v1/order-modification-requests/{requestId}/approve` | Role matrix above | Approved or `PENDING_CANCEL` result | Eligibility, state, stock, or payment conflict |
| `POST /api/v1/order-modification-requests/{requestId}/reject` | Sales Staff / Shop Owner | Rejected result | State conflict |
| `POST /api/v1/order-modification-requests/{requestId}/tax-result` | Accountant | Updated pending/final result | State or invoice-reference conflict |
| `POST /api/v1/order-modification-requests/{requestId}/send-result-email` | Sales Staff / Shop Owner | Email accepted for delivery | Notification failure is logged; committed request is not rolled back |

### Inter-Module Java API

| Provider | Consumer | Contract | Transaction / compatibility |
|---|---|---|---|
| `inventory.api` | `sales` | Reservation release/reallocate for approved store-only request | Participates in the sales transaction or fails atomically. |
| `finance.api` | `sales` | Read Verified payments; create Pending refund instruction; receive refund verification outcome | No finance internal import; external tax call is absent in v1. |
| `audit.api` | `sales` | Consume public modification event after commit | Idempotent by event ID. |
| `iam.api` | `sales` | Current actor and authorization context | Actor ID is server-derived. |

### Domain Events

| Event | Publisher / consumers | Timing | Idempotency |
|---|---|---|---|
| `OrderModificationRequestReviewedEvent` | `sales` -> `audit` | After commit | Event ID / audit unique key |
| `OrderModificationTaxResultRecordedEvent` | `sales` -> `audit`, notification | After commit | Event ID |

## 6. Concurrency, Transaction and Failure Safety

- Lock the request and its order row during approve/reject/tax-result processing. Revalidate request state, delivery state, payment state, invoice linkage, and reservation availability under lock.
- The partial unique index prevents a second active request for the same order.
- Store-only update, reservation change, local invoice update, request state, and audit-event publication are one transaction. Notification is after commit.
- Tax-provider work is never inside the database transaction. The manual result records reference/status in a separate locked transaction; failures keep the request at `PENDING_CANCEL` for retry.
- A replacement order is created/confirmed only by UC-21/UC-22. UC-26 stores only its scalar reference when available.

## 7. Acceptance Criteria and Verification

| AC | Observable outcome | Verification |
|---|---|---|
| AC-01 | UC-20 request is stored once and listed/detail-visible under role rules | Controller/security tests |
| AC-02 | Delivery-blocked request cannot be approved | Service test |
| AC-03 | Draft store-only change atomically updates requested order lines, reservation, invoice, and audit | PostgreSQL integration test |
| AC-04 | Tax-linked change stays `PENDING_CANCEL` until recorded result plus applicable finance outcome | State-transition test |
| AC-05 | Two concurrent approvals of the same request/order yield one transition | PostgreSQL concurrency test |
| AC-06 | Migration is forward-only and preserves V1-V4 history | Flyway Testcontainers test |

## 8. Open Decisions

| Decision | Owner | Status |
|---|---|---|
| Two shared tables are created once in `sales`; UC-20 creates and UC-26 processes them | Product Owner | APPROVED |
| `PENDING_CANCEL` is a request state, not an order status | Product Owner | APPROVED |
| Tax result is recorded manually by Accountant in v1; no provider API | Product Owner | APPROVED |
| UC-64 owns refund approval/execution; UC-26 only creates/observes the pending refund | Product Owner | APPROVED |
