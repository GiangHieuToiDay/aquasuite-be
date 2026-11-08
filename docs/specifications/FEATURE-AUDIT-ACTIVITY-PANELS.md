# Activity Panels for UC-05, UC-10 and UC-24

## Metadata

- **Work ID**: `FEATURE-AUDIT-ACTIVITY-PANELS`
- **Feature / Use Case**: UC-05 User Accounts; UC-10 SKU Variants; UC-24 Customer Debt
- **Version**: 1.0
- **Owner**: AquaSuite team
- **Owning Module**: `audit`
- **Risk Reasons**: public HTTP contract, cross-module query contract, authorization
- **Design Review**: APPROVED
- **Decision Owner**: Store Owner / Technical Lead

## 1. Goal and Scope

The UI designs for UC-05 and UC-10 require a recent activity panel below each management screen. Audit writes already exist in `audit_logs`; this feature exposes safe aggregate read models without moving audit write logic into IAM or Catalog.

### In Scope

- Paginated audit activity by entity type for the account dashboard.
- Paginated audit activity for all SKU variants belonging to one master product.
- Paginated aggregate customer-debt activity below the UC-24 debt dashboard.
- Read-only actor display name enrichment when the actor can still be resolved.
- Frontend panels for UC-05 and UC-10.

### Out of Scope

- New audit tables, migrations, audit write events or Redis storage.
- Global audit administration UI, filtering by arbitrary JSON fields, or UC-07 scope beyond the two panels.
- Changing authorization of audit writes.

## 2. Actors and Security

| Actor | Allowed operation | Required role | Data restriction |
|---|---|---|---|
| Store Owner | Read activity panels | `SHOP_OWNER` | `USER_ACCOUNT`, product variants and customer debt activity |
| Accountant | Read customer debt activity | `ACCOUNTANT` | `CUSTOMER` aggregate only |

The HTTP endpoint permits `SHOP_OWNER` and `ACCOUNTANT`, but the backend permits `ACCOUNTANT` only when `entityType=CUSTOMER`. All other entity types remain Store Owner-only. The frontend hides nothing as a substitute for this server authorization. Passwords, temporary credentials and authentication tokens are never included in audit panel rendering.

## 3. Business Rules

1. `entityType` is required for every audit query.
2. Supplying `entityId` returns activity for that single entity.
3. Supplying no identifier returns recent activity for the requested entity type.
4. `productId` is valid only with `entityType=PRODUCT_VARIANT`; it resolves all variant IDs through the Catalog public API, including soft-deleted variants, before querying audit logs.
5. At most one of `entityId` and `productId` may be supplied.
6. Missing historical actor records do not hide audit rows; `actorName` is omitted.
7. `CUSTOMER` aggregate reads are available to Store Owner and Accountant; all other aggregate reads are Store Owner-only.

## 4. Data and Migration Decisions

- Existing table: `audit_logs` only.
- No schema change or data backfill.
- Existing indexes remain sufficient for bounded, paginated entity-type reads; a future production volume review may add a composite index only with measured evidence.

## 5. Contracts

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| `GET /api/v1/audit-logs` | `SHOP_OWNER`; `ACCOUNTANT` only for `CUSTOMER` | required `entityType`; optional one of `entityId`, `productId`; pageable | `ApiResponse<Page<AuditLogResponse>>` newest first | `SYS_002` for invalid filter combinations; 403 for disallowed entity type |

`AuditLogResponse` adds optional `actorName`; existing fields remain unchanged.

### Inter-Module Java API

| Provider | Consumer | Method/DTO | Transaction | Compatibility |
|---|---|---|---|---|
| Catalog | Audit | `CatalogApi.getVariantIdsByProductId(UUID)` | read-only | Scalar IDs only; no Catalog entity leakage |
| IAM | Audit | existing `IamApi.getUserById(UUID)` | read-only | Failure is tolerated; actor name omitted |

## 6. Concurrency, Transaction and Failure Safety

- Audit is written by existing `AFTER_COMMIT` listeners; the panel may not show a just-committed row until that listener completes.
- Reads use paging and no mutation/locking.
- A missing actor account is non-fatal and does not suppress the audit record.

## 7. Acceptance Criteria and Verification

| AC | Observable outcome | Verification |
|---|---|---|
| AC-01 | Owner can read latest account activity | controller test and Users page panel |
| AC-02 | Owner can read variant activity scoped to selected product | controller test and Variant page panel |
| AC-03 | Invalid filter combinations return `SYS_002` | controller test |
| AC-04 | No new table/migration and no module imports from another module's `internal` package | compile and boundary review |
| AC-05 | UI loading, empty and error states work | frontend tests/build/lint |
| AC-06 | Owner and Accountant can read customer-debt activity only within their approved scope | controller test and UC-24 page |

## 8. Open Decisions

None.
