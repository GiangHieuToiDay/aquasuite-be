# UC-44 — Manage Warranty Policies

## Metadata

- **Work ID**: UC44-WARRANTY-POLICIES
- **Feature / Use Case**: Manage Warranty Policies
- **Version**: v1
- **Owner**: ServiceDesk / FE-09
- **Owning Module**: `servicedesk`
- **Risk Reasons**: data migration, public contract, cross-module
- **Design Review**: APPROVED — requirements supplied by Product Owner on 2026-11-15
- **Decision Owner**: Product Owner

## 1. Goal and Scope

UC-44 owns live warranty-policy definitions by brand and category. A policy change affects only future warranty records; UC-45 must freeze the selected policy terms when issuing a record.

In scope: policy list/search/filter, create/update/detail/deactivate/delete, SKU-reference display, activity logging, Catalog reference validation.

Out of scope: warranty-record issuance (UC-45), claim workflows (UC-46/47), lookup (UC-48), or changing a policy onto a variant (UC-10).

## 2. Actors and Security

| Actor | Operations | Authorization |
|---|---|---|
| Shop Owner | all UC-44 reads and writes | `hasRole('SHOP_OWNER')` |

The REST controller is role-guarded. UI visibility is only a usability aid; it is not an authorization boundary.

## 3. Business Rules

1. A policy has a nonblank name and coverage condition, existing brand/category IDs, and a period greater than zero.
2. There is at most one `ACTIVE` policy for a `(brand_id, category_id)` pair.
3. A new `ACTIVE` policy is permitted when the previous policy for the same pair is `INACTIVE`.
4. Deactivation preserves history and prevents UC-45 from using the policy for future records.
5. Delete is permitted only when no product variant references its ID. The database FK and service precheck both enforce this.
6. All mutations publish one post-commit audit event. Audit persistence failure cannot roll back an already committed policy mutation.

| From | Action | To | Conditions |
|---|---|---|---|
| ACTIVE | deactivate | INACTIVE | Existing warranty records unchanged |
| ACTIVE/INACTIVE | delete | removed | No referencing variant |

## 4. Data and Migration Decisions

- V33 creates `warranty_policies` and `warranty_policy_code_seq`.
- `ProductVariant.warrantyPolicyId` remains a scalar UUID; no cross-module JPA association is created.
- V33 adds the physical FK `product_variants.warranty_policy_id -> warranty_policies.warranty_policy_id ON DELETE RESTRICT` as `NOT VALID`: it checks all new writes without breaking an upgrade if a legacy SKU already has a dangling ID. Those legacy IDs must be reconciled before a later `VALIDATE CONSTRAINT` migration.
- The partial unique index `uq_warranty_policies_active_brand_category` protects the ACTIVE invariant under concurrent requests.
- Before production migration, report non-null `product_variants.warranty_policy_id` values. Any legacy dangling ID requires owner reconciliation; V33 intentionally does not erase it.

## 5. Contracts

### HTTP API

| Method/path | Authorization | Function |
|---|---|---|
| `GET /api/v1/warranty-policies` | Shop Owner | paged search/filter |
| `GET /api/v1/warranty-policies/reference-data` | Shop Owner | active brands/categories for form |
| `GET /api/v1/warranty-policies/{id}` | Shop Owner | policy with referencing variants |
| `POST /api/v1/warranty-policies` | Shop Owner | create |
| `PUT /api/v1/warranty-policies/{id}` | Shop Owner | update |
| `POST /api/v1/warranty-policies/{id}/deactivate` | Shop Owner | deactivate |
| `DELETE /api/v1/warranty-policies/{id}` | Shop Owner | delete if unreferenced |

### Inter-module API

`servicedesk` consumes public `CatalogApi` methods for brand/category lookup, active form options and warranty-policy SKU references. Catalog does not import ServiceDesk internals.

### Domain Event

`WarrantyPolicyChangedEvent` is published in the policy transaction and consumed by Audit after commit in a new transaction. Event ID is used as `audit_logs.audit_id` for idempotency.

## 6. Concurrency and Failure Safety

The unique partial index resolves concurrent ACTIVE policy creates/updates. Service validation provides a friendly error; a `DataIntegrityViolationException` is converted to the same conflict. Deletion is prechecked through Catalog and protected again by FK. No issued `warranty_records` row is modified by UC-44.

## 7. Acceptance Criteria and Verification

| AC | Verification |
|---|---|
| only Shop Owner can operate UC-44 | controller security annotation + manual API test |
| exactly one active policy per brand/category | service and V33 partial unique index |
| referenced policy cannot be deleted | service reference check + FK |
| mutations appear in Activity Log | event/listener after commit |
| frontend follows API contract | `npm run build` |

## 8. Open Decisions

None blocking implementation. UC-45 must explicitly consume only an ACTIVE policy and snapshot its fields when it is implemented.
