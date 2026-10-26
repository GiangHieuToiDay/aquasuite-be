# Flyway database migrations

## Current chain after develop integration (updated 2026-10-26)

This chain is reconciled for a new, empty PostgreSQL database. Java 21 / Spring Boot 3.5.16 manages Flyway. Keep Hibernate ddl-auto=validate and supply credentials through environment variables.

| Version | Migration | Ownership / purpose |
|---|---|---|
| 1 | V1__create_iam_foundation.sql | IAM foundation from develop; UUID users, roles, permissions and joins |
| 2 | V2__provision_canonical_roles_and_protection.sql | Canonical roles and protection from develop |
| 2.1 | V2_1__create_inventory_baseline.sql | Inventory baseline from develop, previously duplicate V2 |
| 3 | V3__create_catalog_categories_and_brands.sql | Categories, brands and their permissions from develop |
| 3.1 | V3_1__extend_iam_and_create_audit_log.sql | IAM provider fields, unique phone index and audit_logs from develop, previously duplicate V3 |
| 4 | V4__create_catalog_baseline.sql | Products, variants, images and bundle_items; uses category/brand tables already created by V3 |
| 5 | V5__add_variant_reference_documents.sql | Variant reference documents from develop |
| 6 | V6__create_crm_customer_and_delivery_addresses.sql | CRM profiles, addresses and users.full_name from develop |
| 7 | V7__SCRUM14_phase1_combo_core.sql | Combo configurations, preflight and configuration backfill |
| 8 | V8__SCRUM14_phase2_combo_options.sql | Component groups and SKU whitelist constraints |
| 9 | V9__password_recovery_storage.sql | Storage required by the retained UserPasswordRecovery entity; no legacy-account backfill |
| 10 | V10__SCRUM08_registration_phone_preflight.sql | Reject noncanonical/duplicate existing user phone values; reuse develop's full_name and phone index |
| 11 | V11__complete_canonical_schema.sql | Add entity-mapped fields missing from the reconciled chain: IAM authentication state and Brand timestamps |
| 12 | V12__create_sales_quotation_schema.sql | Complete V5's quotation placeholder for the UUID-based Sales quotation entities |
| 13 | V13__align_sales_schema_after_merge.sql | Forward-align Sales and purchasing/receiving entities merged after V12; never rewrites applied V11/V12 |
| 14 | V14__create_remaining_module_tables.sql | Create missing AI advisor, fulfillment, finance and service-desk entity tables introduced without migrations |

Flyway interprets underscores within version numbers as dots: V2_1 is version 2.1 and V3_1 is 3.1. This preserves develop's major version order while removing duplicate versions. New local additions continue after V10.

## Removed duplicate or obsolete SQL

- V1__init_iam.sql: incompatible numeric-ID IAM baseline (`BIGSERIAL`, `password`, inline role/permission IDs); superseded by canonical UUID IAM V1. Its still-required authentication-state columns are added safely by V11.
- V2__init_catalog.sql: duplicate catalog baseline superseded by V3 categories/brands and V4 products/variants/images/bundle items.
- V3__init_inventory.sql: duplicate inventory baseline superseded by V2.1; it also used `BIGINT` actor references incompatible with UUID IAM.
- V1__Initial_legacy_schema.sql: superseded by develop's module baselines; creating its numeric-ID users and duplicate catalog/inventory tables would conflict.
- V4__integrate_uuid_authentication.sql: obsolete on develop's UUID IAM foundation; must not rename/recreate its existing users or roles.
- V7__SCRUM08_customer_profiles.sql: customers already exists from develop V6; keep develop's Customer schema and foreign-key behavior.
- Former registration V6: full_name and the unique phone index already exist. Only the protective phone preflight remains, now V10.
- Former recovery V5: retain CREATE TABLE as V9 because an entity/repository still maps user_password_recovery; remove backfill from nonexistent iam_legacy_users/iam_legacy_user_ids.
- Former combo V2: quantity, uniqueness and bundle-item foreign keys already exist from develop V4. Retain combo preflight/configuration/backfill and useful indexes, without duplicate constraints. Do not create the unused audit_entries table; develop owns audit_logs.
- Develop V4 formerly recreated brands/categories and their index after V3. Those duplicate statements are removed; V3 remains their owner.

## Existing databases and historical migration chains

Renamed/removed SQL and the reconciled V4/V7/V9 content change migration identity/checksums. This is a new-database chain, not an automatic upgrade path for any previously migrated database. Historical V1-V7 references in older specs/reviews describe the former chain, not the current numbering. Earlier test totals do not verify this new chain.

For an existing shared/production database, back up and inspect flyway_schema_history and actual schema before choosing a separate reviewed forward-upgrade/onboarding procedure. Do not automatically baseline, repair, delete history, rewrite applied migrations or recreate a database.

On 2026-10-26 the developer's local database was inspected before migration: the affected V5 placeholder tables contained zero rows. The missing versions were applied with Flyway's one-time out-of-order mode, then V11 and V12 were applied normally. After a later branch merge introduced a duplicate Sales V11 and additional unmigrated entities, the duplicate migration was removed from discovery and its intended current schema was reconciled forward through V13 and V14. Out-of-order mode remains disabled. The database now reports schema version 14.

V12 intentionally keeps UUID identifiers consistent with IAM, CRM and catalog. It creates `quotations` and `quotation_payment_milestones`, and expands V5's empty `quotation_items` placeholder to match the current Sales entities.

The removed `V11__create_sales_quotations.sql` must not be restored or merely renumbered: it duplicates applied version 11, drops `quotation_items`, and conflicts with V12. V13 performs the compatible forward transition instead. V14 completes other entity tables exposed by full Hibernate validation after the merge.

## Verification

On 2026-10-26, Flyway applied all 15 migrations through V13 to a fresh PostgreSQL 17.6 Testcontainers database and Hibernate initialized the tested persistence context. V13 and V14 were then applied to the inspected local PostgreSQL database; full Hibernate validation passed and the complete application started successfully on port 8080 at schema version 14.

On 2026-10-21, all 12 SQL files ran in version order against an isolated disposable PostgreSQL 17.6 container, each in a transaction with ON_ERROR_STOP. All passed. Checks confirmed the required IAM/CRM/inventory/catalog/combo/recovery tables, UUID users, exactly one unique phone index and absence of unused audit_entries. Container was stopped and removed after verification; no ports were exposed.

This directly verifies PostgreSQL SQL execution, not Flyway history/checksum/retry behavior or complete Hibernate validation. The pre-merge migration tests still refer to the old chain and need reconciliation before their results can be used as evidence for this chain. Test compilation also has a previously identified missing ArchUnit dependency in the merged POM. No full application/integration pass is claimed.

## Full merged-code verification (2026-10-21)

Production and test sources compile after restoring the ArchUnit test dependency and phase1-it Failsafe profile. Registration's three public endpoints are retained in develop's security configuration. Test fixtures use the develop User/IamApi model.

Executed `mvn -o clean -Pphase1-it verify`. Surefire failures prevented the lifecycle from reaching Failsafe, so the remaining integration suite was executed explicitly with `mvn -o -Pphase1-it failsafe:integration-test failsafe:verify`.

| Suite | Tests | Failures | Errors | Skipped |
|---|---:|---:|---:|---:|
| Surefire | 362 | 2 | 185 | 0 |
| Failsafe | 70 | 2 | 66 | 0 |
| Total | 432 | 4 | 251 | 0 |

BUILD FAILURE. Most context errors originate from a develop schema/model mismatch: Product maps products.origin but no current migration adds that column. Additional findings: GlobalExceptionHandler has overlapping exception mappings after merge; migration tests still assume the previous numeric-user/legacy V1-V7 chain and old phone-index name; configuration and DTO structural tests retain assumptions superseded by develop. These require reconciliation; the passing direct SQL execution above does not establish that the application starts or all tests pass.

Reports: target/surefire-reports and target/failsafe-reports. No live database migration, commit or push was performed.

## Product origin removal (2026-10-21)

At the user's request, origin is removed from Product, create/update requests, product detail/list/storefront responses, service mappings and the product Postman collection. No migration is needed because the reconciled schema never created this column. The missing-origin finding above is historical; the handler ambiguity has also been fixed separately. Full verification is rerun after this change.

Post-origin verification: `mvn -o clean -Pphase1-it -Dmaven.test.failure.ignore=true verify` executed both suites to completion (2026-10-21 22:24:06 +07). Surefire: 363 tests, 2 failures, 147 errors, 0 skipped. Failsafe: 70 tests, 7 failures, 27 errors, 0 skipped. Total: 433 tests, 9 failures, 174 errors, 0 skipped. The ignore flag allows collecting all suite results; Maven's BUILD SUCCESS here does not mean tests passed. Full application startup test passed and missing-origin validation is resolved. Remaining failures include Brand initializer IDs, integration fixtures/configuration/DTO assumptions and obsolete migration expectations; no overall test PASS is claimed.
