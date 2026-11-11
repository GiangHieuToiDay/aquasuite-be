# WORK-023 — Combo in quotations

## Metadata
- Owner / decision owner: repository owner.
- Owning module: sales.
- Risk: L — monetary snapshots, additive schema and quotation-to-order conversion.
- Design Review: APPROVED — owner confirmed the concrete design on 2026-11-08.
- Functional scope approved: owner agreed to preview/create/update quotations containing combos and conversion preserving package pricing and component SKUs.

## 1. Goal and scope
Sales catalog selection 1a733086-366b-4fa3-ac6c-c81d406f5822 is Product BUNDLE combo-001,
with package price 35,000,000 and three components. It is not a ProductVariant.
QuotationItemRequest currently accepts variantId only; validation resolves every selection as a SKU,
and the entity requires variantId. The baseline SQL already has nullable variant_id, combo_id,
item_type and parent_item_id, with real variant/product foreign keys; reuse these existing columns.

Support combo selection in preview/create/update, saved detail, copy/revision and order conversion.
Preserve ordinary SKU behavior, current approval/VAT/deposit rules and Finance/Inventory implementations.
Direct POS combo pricing is outside this change. No live data rewrite or automatic backend restart.

## 2. Actors and security
Retain existing quotation/approval/conversion roles and authorization. Catalog reads go through
CatalogApi only; no cross-module internal imports or JPA relationships. No permission changes.

## 3. Representation and business rules
- Keep the existing request selection field variantId for compatibility. Resolve a real SKU first;
  if absent, resolve it as a bundle Product ID through CatalogApi.getBundleSummary.
- Unknown selection still returns the existing not-found error. Inactive/empty/invalid combos or
  missing/inactive components are rejected. Combo quantity must be a positive whole number.
- Store a root quotation item with combo_id, name/code snapshot, unit Bo, package price,
  requested combo quantity and line discount; its variant_id is null.
- Store children with real component variant IDs, snapshot SKU/unit/retail price/cost and
  total quantity = requested combo count * component count; parent_item_id links to the root.
- Root price/cost are package price / aggregate component cost per combo. Existing calculator
  isComponent behavior excludes children from quotation financial totals, preventing double counting.
- Response root uses the existing components array; legacy variantId returns combo_id for a combo
  selection, with an additional optional comboId field for clarity. Real SKU responses stay unchanged.
- Existing saved quotes retain their price/composition snapshots when Catalog changes. New edits
  may resolve the current definition explicitly through the existing update flow. Copy/revision
  must handle root/child IDs, names and costs consistently; it must never treat a bundle root as a SKU.
- Backorder metadata for a combo derives from its components, never independent combo inventory.

## 4. Data and migration
New migration V23 (subject to confirming the next free version immediately before editing):
- Retain nullable quotation_items.variant_id and its existing FK for every nonnull variant.
- Map existing scalar combo_id/item_type; add a nullable combo-name snapshot column.
- Enforce valid product identity for new inserts with a NOT VALID check: real variant_id OR
  a combo root with combo_id, name and no parent. Existing legacy rows are not rewritten/validated.
- Reuse existing parent_item_id; never create fake variants or insert bundle IDs into variant FKs.
- No order_items schema change; downstream inventory/order consumers continue receiving real SKUs.
- Add optional bundleCode to the existing CatalogBundleSummaryDto and populate it from the existing
  BundleResponse productCode; callers using builders remain compatible and quotation roots keep the real code.

## 5. Conversion and pricing
- A combo root is not an OrderItem/SKU. Expand its saved children into real OrderItems with comboId.
- Preserve accepted package net amount after the root's quotation discount, regardless of subsequent
  Catalog price/composition edits. Use the stored child SKU prices as allocation weights.
- Allocate the package net to component lines in currency minor units, using proportional allocation
  with stable rounding/remainder distribution. No lost cent or negative component discount.
- Child unitPrice remains its snapshot retail price; discountAmount includes its allocated package
  saving plus quotation discount. Thus child gross subtotal can exceed the package subtotal while
  child net totals sum exactly to the quotation package net. VAT/shipping and final order total
  remain inherited from the accepted quotation, once only.
- Only component quantities enter stock checks/holds, including the existing 20%-deposit auto-hold
  flow. Existing automatic registration includes these real OrderItems only.
- Noncombo conversion keeps its current calculations and legacy/deferred-stock decisions.

## 6. Transactions and failure safety
Existing quotation/order transaction boundaries remain. Save root and children atomically;
update/delete/copy must preserve parent relationships. Any invalid component or allocation rolls
back the operation. Preview remains read-only. No receipt or stock write is added to quotation preview/save.

## 7. Verification / acceptance criteria
- Exact reported ID previews and saves as one package with three component lines; no SKU-not-found.
- Mixed normal SKU/combos, multiple combo quantities, discounts, VAT and shipping total correctly.
- Reject invalid/inactive bundle/components, fractional combo quantities, duplicate selections and bad pricing.
- Create/update/detail/copy/revision retain grouping, snapshot names and component stock metadata.
- Conversion preserves package net including rounding edge cases, real component IDs/quantities,
  comboId and no double counting; cọc threshold/Finance and existing stock hold regressions pass.
- Migration preserves existing SKU-only quotations and their real variant FK constraints.
- PostgreSQL integration and existing quotation calculator/mapper/service/validation tests pass.

## 8. Required review
Owner must approve the nullable-variant bundle-root schema and monetary conversion model above
before production edits, per docs/workflow/feature-delivery.md Risk L. General scope approval is
recorded separately and is not represented as approval of these newly discovered schema decisions.

## 9. Minimal revision-constraint repair
Integration found obsolete uq_quotations_code still enforcing code-only uniqueness alongside the
existing uq_quotations_code_revision index. Owner requested the smallest fix after this conflict
was reported. V24 drops only the obsolete constraint; the existing (quotation_code,revision_no)
uniqueness stays enforced. No baseline edit or data rewrite. This enables existing revision behavior,
including combo snapshots, without changing workflow/approval rules.
