# UC-13: ordinary Combo category

Owner-approved revision, 2026-11-04. This supersedes the client-selected category/brand write behavior in UC-13 for Combo management only.

## Contract
FE omits categoryId and brandId. CreateBundleRequest/UpdateBundleRequest retain optional legacy fields for existing callers, but their values are ignored. Creating a Combo assigns the category with code COMBO and a null brand. Updates preserve historical category/brand links. Existing response metadata remains compatible; FE does not show classification fields. Publication validates components, price, schedule and lifecycle, without requiring historical classification to be active.

## Storage and scope
Product entity, products.category_id NOT NULL, category foreign key and nullable brand_id remain unchanged. No Category service, IAM, Inventory or Sales source changes.

CatalogDataInitializer seeds a category named Combo with code COMBO when no category with that code exists, including databases that already have other categories. V16 was retired at the owner request; the existing startup Flyway repair strategy reconciles applied history without deleting category data. It never overwrites an existing row. Per owner revision, Combo is an ordinary category: there is no protection trigger/function, root-only rule, immutable code or restriction against deactivation/renaming beyond normal Category rules. Product foreign keys still prevent deleting referenced categories, as for any product.

The data initializer seeds the row at application startup. Per owner revision on 2026-11-08, category creation belongs exclusively to CatalogDataInitializer; bundle requests only look up the existing row and reject creation if it is missing. Application lookup uses code COMBO, never a hardcoded UUID. Creating a product requires an ACTIVE category; the category may be a child like any ordinary category.

## Deployment and verification
Apply through the existing Flyway startup process when deploying this backend revision. Never edit a baseline migration or manually mark V7 applied. The retired V16 slot is not reused or renumbered. Existing COMBO rows and product references remain unchanged. Live migration and backend restart have not been performed by this task. Isolated PostgreSQL tests and frontend verification are recorded in the local delivery brief.

The local database inspected on 2026-11-04 already recorded a different V7, allow_bundles_without_classification. This seed-only source does not repair that database's migration history or restore its old category nullability. Do not mark the new seed as applied without executing/reconciling it. No extra migration version or live repair was introduced in the simplification task.

Verification of ordinary-category revision: 39 PostgreSQL integration/regression tests passed, including fresh V1-V7 migration/validation, Combo create/update, child-category assignment and ordinary category edits without a protection trigger. This does not certify the existing application database's mismatched V7 history.
