# UC36 history and approved releases on aquasuite-test

Base: `/api/v1/sales/orders/{orderId}/stock-reservations`. Existing UC29 endpoints and retry through ReservationAllocationRecovery remain unchanged.

| Method/path | Role | Behavior |
|---|---|---|
| GET /history | SALES_STAFF, ACCOUNTANT, SHOP_OWNER, ADMIN, SUPER_ADMIN | Reservation quantities, activity history, modification decision metadata, execution results, referenced actor names |
| POST /releases/{decisionId} | SHOP_OWNER | Execute a completed owner-approved cancellation/reduction; repeated calls return the original execution result |

Standard ApiResponse; successful calls return 200. POST needs no body: caller never supplies quantity, warehouse, approver or executor. User identity is resolved through existing IAM APIs. Read access covers all orders for the permitted staff roles; write access is owner-only even when another role can read.

Supported decisions: APPROVED FULL_CANCELLATION, or ITEM_CHANGE with only REMOVE/REDUCE. The stored reviewer must currently resolve as SHOP_OWNER and reviewedAt must be present. The decision must belong to the requested order. Pending/rejected decisions cannot execute.

Release only excess actual held quantities at WH_MAIN, not waiting or consumed quantities. Example: held 5, original request 8, retain 2 -> release 3; retain 6 -> release 0. Zero results are persisted for safe retries. Physical on-hand and unowned legacy aggregate holds are unchanged.

History decision releaseStatus: PENDING_APPROVAL, PENDING_UC26_COMPLETION, PENDING_RELEASE, COMPLETED, NOT_APPLICABLE. Actor names are current display names, fallback username; missing users are omitted. IDs remain available. Financial data is not included.

UC36_001=404 decision missing; UC36_002=409 invalid approval; UC36_003=409 unsupported/stale reservation or decision; AUTH_002=403 role denial. Existing inventory errors propagate atomically.

After a PENDING_CANCEL/APPROVED quantity-changing decision, the original immutable UC29 selection cannot allocate stock again; it returns SALES_DEPOSIT_008 (RESERVATION_SELECTION_CONFLICT). No automatic quantity/price/invoice mutation is performed by UC36.

V38 moves V35 execution results into the existing sales_reservation_operations ledger, using the decision ID as request_id and a UC36_RELEASE: payload prefix, then drops sales_reservation_releases. This includes zero-release results; request-ID collisions abort migration instead of overwriting existing operations. V35/V36 remain immutable applied history. V36 still permits CONSUME in the existing Inventory ledger. Apply via reviewed normal Flyway deployment. No application DB/history, finance/COD, IAM or UC26 approval code is changed by this task. On this checkout the existing UC26 service exposes rejection, not a complete approval workflow: UC36 executes only already-persisted valid approvals and does not add an approval bypass.

The earlier V32/V33 local Flyway reconciliation remains a separate pending deployment step. Do not copy the old branch V30 migration or overwrite current migration history.
