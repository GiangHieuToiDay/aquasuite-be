# UC-48 — Look up Warranty Information

## Metadata

- **Work ID**: UC48-WARRANTY-LOOKUP
- **Owning Module**: `servicedesk`
- **Risk Reasons**: customer authorization, public cross-module read contract
- **Design Review**: APPROVED — customer identity derives exclusively from the authenticated session; requirements supplied 2026-11-15

## Scope

UC-48 is read-only. It lists only warranty records of the CRM customer linked to the authenticated `CUSTOMER` account, filters that list by serial/order code, derives active/expired from dates, and exposes no mutation or claim submission.

Out of scope: record issuance (UC-45), claim creation (UC-47), warranty policy administration (UC-44).

## Security and Data Flow

`GET /api/v1/customer/warranties?keyword=` requires `ROLE_CUSTOMER`. The controller accepts no customer ID. ServiceDesk obtains `userId` from IAM, resolves the linked customer through `CrmApi.getCustomerByUserId`, then reads only `WarrantyRecord.customerId` for that ID.

WarrantyRecord uses scalar `variantId` and `orderItemId`; it enriches display through public `CatalogApi.getSkuSummary` and new public `SalesOrderApi.getOrderItemSummary`. No cross-module entity relationship or direct repository import is introduced.

## Business Rules

1. Customer without an associated CRM profile or without warranty records receives an empty list.
2. Search matches only serial number or resolved order code, case-insensitively.
3. `ACTIVE` is derived when today is not after `endDate`; `EXPIRED` otherwise. Remaining days is `max(0, endDate - today)`.
4. Warranty terms are returned from the frozen `warranty_records.warranty_terms`, never from live UC-44 policy data.
5. Claim link is allowed only for derived ACTIVE records; it points to future UC-47 and does not submit a claim.

## Contract

| Contract | Description |
|---|---|
| `GET /api/v1/customer/warranties` | Customer-safe list of `WarrantyLookupResponse` |
| `SalesOrderApi.getOrderItemSummary(UUID)` | Read-only order code/item display snapshot for scalar order-item consumers |

## Verification

- Service unit tests validate customer scoping, date-derived status and serial/order filtering.
- Frontend build validates the customer page/API integration.
