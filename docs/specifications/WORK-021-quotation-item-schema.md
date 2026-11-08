# WORK-021 — Remove unused required quotation item columns

## Metadata
- Owning module: sales. Risk: L (schema removal).
- Design review: APPROVED by the user's explicit request to remove the unused fields after reviewing their current usage.

## Goal and scope
Quotation creation fails because PostgreSQL requires `item_name_snapshot` and `net_unit_price`, although neither field is mapped or consumed by the current Java implementation. Remove these two columns through V21; leave quotation entities, calculations, requests, responses and other modules unchanged.

## Data and migration
Keep applied V1 unchanged. `V21__remove_unused_quotation_item_snapshots.sql` drops only these columns from `public.quotation_items`, without CASCADE, row deletion or renumbering existing migrations. Fresh databases also finish without the columns. Any historical values in these two columns are discarded by the approved removal; existing item IDs, quantities, prices, relationships and other columns remain. There is no automatic restoration of discarded values. No live database migration is executed by the agent.

## Contracts and security
No HTTP, Java API, event, authorization or credential changes. The existing quotation implementation continues to resolve names from Catalog and calculate prices from saved unit prices/discounts.

## Transaction and failure safety
Use the existing Flyway startup migration mechanism and transactional PostgreSQL DDL. No automatic schema recreation, baseline edit or additional repair behavior.

## Acceptance and verification
- A fresh PostgreSQL database applies all migrations successfully and validates.
- A V20 database with an existing quotation item upgrades to V21; both unused columns disappear, and the remaining item data is preserved.
- The existing INSERT column set succeeds after upgrading without either removed field.
- Existing quotation service, mapper, validation and calculation tests continue to pass.
