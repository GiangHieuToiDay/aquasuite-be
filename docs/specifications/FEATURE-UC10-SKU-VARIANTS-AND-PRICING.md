# UC-10 — Manage SKUs, Variants, and Pricing

## Metadata and Governance

- **Feature ID**: `UC-10` / `FE-03`
- **Version**: `0.1.2`
- **Owning Module**: `catalog`
- **Repository Scope**: `BE (aquasuite-be)`
- **Target Package**: `com.aquasuite.ims.module.catalog`
- **Database Baseline**: PostgreSQL; canonical forward-only Flyway chain V1–V14
- **Spec Review Status**: `APPROVED`
- **Risk Level**: `HIGH` — pricing, cross-module inventory initialization, and irreversible hard deletion

## 1. Business Context and Scope

UC-10 lets a Store Owner manage saleable SKU variants belonging to an existing master product (UC-09). A variant owns its SKU, attributes, technical JSON specification, unit conversion, cost/retail price, warranty reference, serial-tracking flag, and sale status.

### In scope

- List, filter, create, update, deactivate/reactivate, and conditionally hard-delete variants.
- Create a main-warehouse (`WH_MAIN`) inventory record with zero on-hand and reserved quantity for every newly-created variant.
- Atomic Color × Size matrix generation.
- SKU uniqueness, price validation/warning, audit events, and deletion-eligibility detail.

### Out of scope

- Barcode support and wholesale/project-tier prices. Project/contractor prices are negotiated on quotation/order lines.
- Capturing serial numbers; this occurs at stock dispatch.
- Restoring an intentionally deleted SKU from the UI.

## 2. Actors, Authorization, and Data Protection

| Actor | Required role | Authorized operations |
|---|---|---|
| Store Owner | `ROLE_SHOP_OWNER` | All UC-10 operations, including retail/cost-price changes and hard deletion |
| Other roles | Any | No UC-10 mutation or deletion operation |

No credential or personally sensitive value is in the UC-10 payload. Audit records store a variant snapshot and an optional deletion reason; they never store user secrets.

## 3. Business Rules

1. `sku` is globally unique, including across products. It is trimmed before lookup/persist. A duplicate request returns `409 SKU_003` / MSG41. `SKU_001` is reserved for an unknown master product.
2. `conversionRate` must be greater than zero. Weight and non-null prices must be non-negative.
3. `sellingPrice < costPrice` is permitted only when the request explicitly acknowledges the warning; otherwise the service returns a warning-required business error without persisting.
4. A missing retail selling price always results in `INACTIVE`; a caller cannot force such a variant to `ACTIVE`.
5. A created variant initializes exactly one idempotent `WH_MAIN` inventory record with zero on-hand/reserved quantity.
6. Matrix creation is all-or-nothing. Duplicate SKUs inside the request and against persisted variants abort the entire transaction.
7. `INACTIVE` variants cannot be added to new carts, quotations, or orders. Reactivation is allowed when a retail selling price exists.
8. Only Store Owner can request deletion. Deletion is permanent, releases the SKU, and cannot be restored in UI.

### Hard-deletion eligibility

The system returns a complete eligibility report before final confirmation. Hard deletion is blocked with `409 SKU_007` when any condition below is false:

- Every inventory row for the variant has both `on_hand_qty = 0` and `reserved_qty = 0`.
- Reference counts are zero for `goods_receipt_items`, `stock_movements`, `stock_dispatch_items`, `quotation_items`, `order_items`, `sales_invoice_items`, `stock_count_items`, `purchase_order_items`, and `delivery_items`.
- The variant is not a `bundle_items` component or a selected/default component-group option of a product whose status is `ACTIVE`.

`cart_items` do not block deletion. On the final confirmation they are deleted, together with the zero-quantity inventory row, variant-specific images, and inactive-bundle memberships. For inactive component groups, a default-SKU group is removed and a non-default allowed SKU is removed from its allowed list before the variant itself is deleted. The deletion and its complete pre-delete snapshot/reason are recorded in `audit_logs` after commit. If blocked, the response returns the concrete counts and the UI may immediately invoke the deactivate endpoint instead.

## 4. State Lifecycle

| From | Action | To | Conditions |
|---|---|---|---|
| New | Create without retail price | `INACTIVE` | Valid SKU and fields |
| New | Create with retail price | `ACTIVE` or requested `INACTIVE` | Valid SKU and fields |
| `ACTIVE` | Deactivate | `INACTIVE` | Store Owner |
| `INACTIVE` | Reactivate | `ACTIVE` | Retail price present; Store Owner |
| `ACTIVE` / `INACTIVE` | Hard delete | Removed | All deletion checks pass; Store Owner; final confirmation |

`DELETED` is retained only as a legacy enum value. UC-10 does not persist it because permanent deletion must release the SKU for reuse.

## 5. Data Model and Forward Migration

`product_variants`, `product_images`, `bundle_items`, `inventories`, `stock_movements`, and `stock_count_items` exist in the canonical baseline. V5 added the original document-line/cart references used by this feature: `goods_receipt_items`, `stock_dispatch_items`, `quotation_items`, `order_items`, `sales_invoice_items`, `carts`, and `cart_items`. Later owner-module migrations V13 and V14 added `purchase_order_items` and `delivery_items`, also with physical `variant_id` foreign keys. UC-10 does not own or modify those schemas, but its hard-deletion guard must count every such reference.

Document header ownership remains outside UC-10. UC-10 only consumes scalar variant-reference counts through public contracts; it does not create, update, or delete header or historical line records.

No old Flyway migration is edited. The migration must be additive and compatible with the V1–V4 column names and types already applied.

Cross-module relationships remain scalar IDs in Java. Catalog must not import Inventory/Sales entities or repositories.

## 6. API and Contract Design

### HTTP endpoints

All endpoints return `ApiResponse<T>`.

| Method and URI | Purpose |
|---|---|
| `GET /api/v1/catalog/products/{productId}/variants` | Paginated/filterable variant list and summary cards |
| `POST /api/v1/catalog/products/{productId}/variants` | Create one variant; returns MSG39 |
| `POST /api/v1/catalog/products/{productId}/variants/matrix` | Atomically create Color × Size variants; returns MSG40 |
| `PUT /api/v1/catalog/products/{productId}/variants/{variantId}` | Update editable variant/pricing fields |
| `PATCH /api/v1/catalog/products/{productId}/variants/{variantId}/status` | Deactivate/reactivate |
| `GET /api/v1/catalog/products/{productId}/variants/{variantId}/deletion-eligibility` | Return counts/checklist used by the two-step dialog |
| `DELETE /api/v1/catalog/products/{productId}/variants/{variantId}` | Permanently delete after explicit confirmation and optional reason |

Mutation endpoints require `ROLE_SHOP_OWNER`. The delete request contains `confirmed: true` and optional `reason`; the eligibility endpoint is read-only and never mutates data.

### Inter-module contracts

`InventoryApi` will gain public, entity-free operations to initialize a main-warehouse record and return/deallocate the inventory-side deletion usage of a variant. A new Sales/Cart public query contract will return reference counts and delete cart items in the caller transaction. Catalog only consumes those public APIs.

### Events and audit

Catalog publishes immutable public records for variant creation, update, status change, and deletion. The audit module consumes them after commit in a new transaction, idempotent by `eventId`. Deletion uses the event's snapshot because the entity no longer exists after commit.

## 7. Transactions, Concurrency, and Failure Recovery

- Single create/update and each matrix batch are `REQUIRED` transactions.
- SKU uniqueness is enforced by `uq_product_variants_sku`; database duplicate-key races map to MSG41.
- Deletion acquires a pessimistic write lock on the variant before it reads final eligibility. It locks inventory rows and re-evaluates every document, inventory, and active-combo reference inside that same transaction, then removes permitted dependents in FK-safe order before deleting. A changed reference between eligibility preview and confirmation causes `SKU_007`, never an exposed foreign-key failure.
- Inventory initialization is idempotent to protect retries. If it fails, the surrounding variant transaction rolls back.
- Audit is an after-commit side effect. An audit failure never restores a permanently deleted variant; the listener logs and supports operational reconciliation.

## 8. Acceptance Criteria

- [ ] A Store Owner can create a globally unique SKU and receives MSG39; exactly one zero-quantity `WH_MAIN` inventory row exists.
- [ ] Matrix creation persists every valid Color × Size row or none of them, and returns MSG40 with the created count.
- [ ] A duplicate SKU, invalid conversion rate, or negative price is rejected; a below-cost price requires acknowledgement.
- [ ] Missing retail price forces `INACTIVE`; inactive variants are excluded from sale eligibility.
- [ ] Status/pricing changes are audited and do not rewrite existing quotation/order/invoice price snapshots.
- [ ] Eligibility reports exact reference and inventory counts, including purchase-order, delivery-item, and active component-group-option references.
- [ ] A referenced/reserved/nonzero/active-bundle variant returns `409 SKU_007`; deactivation remains available.
- [ ] An eligible confirmed deletion removes allowed dependents and variant atomically, records audit data, and allows the SKU to be recreated.
- [ ] Non-Store-Owner mutation/deletion requests are forbidden.

## 9. AC-to-Test Matrix

| AC | Verification |
|---|---|
| Create/matrix/uniqueness | Service + PostgreSQL/Flyway integration tests, including concurrent duplicate SKU creation |
| Pricing/status | Unit and MockMvc validation/security tests |
| Inventory initialization | Contract integration test through `InventoryApi`; retry remains one row |
| Deletion guard | Seed each reference table independently and assert exact count plus `SKU_007` |
| Eligible hard deletion | Integration test for FK-safe dependent deletion, audit snapshot/reason, and SKU reuse |
| Audit | `@TransactionalEventListener(AFTER_COMMIT)` idempotency test |
