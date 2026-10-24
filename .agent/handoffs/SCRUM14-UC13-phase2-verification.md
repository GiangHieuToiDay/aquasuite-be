# SCRUM14 / UC-13 Phase 2 verification

IMPLEMENTATION_STATUS: READY_FOR_REVIEW
PLAN_ID: SCRUM14-UC13
PLAN_VERSION: 0.10.0
SPEC_VERSION: 0.10.0
AUTHORIZATION: Direct user instruction to implement Phase 2; same-price options, one warehouse, defer Auth/Audit/media.
TECHNICAL_REVIEW: PENDING
FINAL_GATE: NOT_GRANTED

## Lanes Completed

- Data/validation: optional draft dates, publication dates required; component groups and current-price whitelist validation; additive V3.
- Publication: locked/versioned publish/unpublish; current-version no-op, stale conflict, future publication and expired/inactive checks.
- Public/preview: visibility before paging; public detail and selection evaluations; same-price replacement retains quantities and rejects duplicate final SKU.
- Inventory: additive api contract using existing batch read, single warehouse, REQUIRES_NEW/read-only to isolate query failures; no inventory mutations.
- Verification/docs: full clean verify, regression updates, source diff check and handoff. No extra agent, framework, cache, Auth/media scaffolding or Sales subsystem.

## Files Changed

Only the following paths were edited/created for this P2 task. Other pre-existing uncommitted Phase 1 changes remain in the workspace and are not claimed as new P2 work.

| Repository | Relative Path |
|---|---|
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/entity/BundleConfiguration.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/entity/BundleComponentGroup.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreCreateRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreUpdateRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreDetail.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleComponentGroupRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleVersionRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleEvaluationRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleEvaluation.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/mapper/BundleCoreMapper.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleAdminService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleStorefrontService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/controller/BundleAdminController.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/controller/BundleStorefrontController.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/api/InventoryAvailabilityApi.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/internal/service/impl/InventoryServiceImpl.java` |
| BE | `src/main/resources/db/migration/V3__SCRUM14_phase2_combo_options.sql` |
| BE | `src/test/java/com/aquasuite/ims/support/BundleCoreTestSupport.java` |
| BE | `src/test/java/com/aquasuite/ims/support/FlywayMigrationIT.java` |
| BE | `src/test/java/com/aquasuite/ims/architecture/BundleCoreBoundaryTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/migration/BundleCoreMigrationIT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleCoreHttpIT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundlePhase2IT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleStorefrontServiceTest.java` |
| BE | `docs/specs/SCRUM14-UC13-product-combos.md` |
| BE | `docs/README.md` |
| BE | `docs/migrations/flyway.md` |
| BE | `.agent/plans/SCRUM14-UC13.md` |
| BE | `.agent/handoffs/SCRUM14-UC13-phase2-verification.md` |

## Implementation Summary

Reuse BundlePricingService, BundleCoreMapper/MapStruct, BundleDetailAssembler, aggregate lock/version helpers, core requests/detail and batched catalog loading. One new storefront service/controller handles public filtering and price/stock preview. Core admin writes do not call inventory. Option whitelist is an element collection stored in a separate table, with a UUID[] column; no separate option repository or generic abstraction. Batch size 100 avoids one collection read per bundle in a normal page.

Auth/Audit/media were explicitly deferred by the user. Publication therefore does not require banner/asset/actor. No fake identity or stock provider is installed. Existing one-warehouse Inventory reader is reused through a minimal new api interface, preserving legacy facade/reservation behavior.

## Tests Added / Updated

- BundleStorefrontServiceTest: 9 cases, including six exact time boundaries for [from,until), dependency outage, retail-price drop, hidden draft and no Inventory call for hidden IDs.
- BundlePhase2IT: 14 cases on PostgreSQL: version/no-op/stale, draft/expired/inactive publication, future visibility and filtering before paging, current-price option replacement and quantity/price multiplication, actual reserved quantity unchanged, missing/negative stock, invalid whitelist/duplicate groups/quantity, same-price drift at publish/evaluation, atomic group/date update, duplicate final SKU/inactive selection, failed group-write rollback, HTTP publication flow, real SQL dependency failure isolation, and competing publish/update.
- Boundary test includes new Catalog classes; cross-feature dependencies only reach inventory/api.
- HTTP regression uses unknownGroups as its unknown field because componentGroups is now supported.
- V2-specific migration regression is explicitly pinned to target 2. Flyway lifecycle regression covers fresh V1/V2/V3 and V2 adoption/upgrade preserving prices/versions.
- Shared test configuration imports the real Inventory provider; a test-only IAM mock satisfies its legacy constructor. No preview uses that actor provider.

## Verification Performed

- PASS: `./mvnw.cmd clean -Pphase1-it verify` (outside sandbox for Docker pipe access), exit 0, BUILD SUCCESS at 2026-10-20 15:05:17 +07.
- PASS: 56 Surefire + 55 Failsafe = 111 tests; 0 failures, 0 errors, 0 skipped. XML totals independently summed.
- PASS: generated MapStruct and complete source/test compilation from clean target; PostgreSQL 17.6/Testcontainers, Flyway V3 and Hibernate validation.
- PASS: `git diff --check`; reviewed P2 changes for secrets, package boundaries and preservation of initial uncommitted work.
- Historical attempts: initial sandbox run could not access Docker. First full run found one obsolete unknown-field assertion; fixed and rerun. Intermediate 109-test run passed; final clean run includes two additional real SQL/concurrency tests.
- Local ignored log: `uc13-phase2-clean-verify.log`. Reproducible XML reports under target/surefire-reports and target/failsafe-reports.
- NOT_RUN: deployment/live database, external media/IAM, independent review/Final Gate and Sales integration.

## Contract Verification

- B03-B06: existing request fields retained; dates/componentGroups added. Old core constructors/JSON still save drafts. PUT is full replacement: clients must include current dates/groups to retain them; omitted dates clear them and omitted/null groups becomes []. Existing PUBLISHED definition must be unpublished before edits.
- B07/B08: documented POST publish/unpublish routes; required expectedVersion; stale checked before no-op. Message MSG44 on publish. Draft unpublish rejected. Soft delete/status remain independent of publication and stock.
- B01/B02: public GET list/detail at /api/v1/catalog/bundles, ACTIVE/PUBLISHED + UTC [from,until). Visibility applies before pagination and direct ID reads. No costPrice in public DTOs.
- B09: POST /{bundleId}/evaluations is read-only preview, quantity 1..10000, optional expectedVersion, whitelisted selections. Price evaluated using current SKU selling prices; no surcharge, duplicate final SKU or silent package-price change. Totals checked for DECIMAL overflow.
- Inventory availability is onHand-reserved at the single existing warehouse. Missing/failed read is UNKNOWN, never purchasable; known shortage lists variant IDs. Real SQL failure does not mark the Catalog transaction rollback-only.
- Shared ApiResponse and existing exception handler/error codes preserved. Validation/binding/query failures remain 400; invisible IDs 404; stale writes/evaluation 409.
- V1/V2 were not edited by this P2 task. V3 only adds option storage. No entity links cross feature boundaries.

## Deviations From Approved Plan

There is no independently approved/hash-matched 0.10.0 plan artifact; implementation relies on the user's direct authorization and recorded scope decisions, as in prior revisions. No independent APPROVED review is claimed. Implementation matches the scoped 0.10.0 revision. Historical proposals requiring media/actor or unconstrained option pricing are superseded only for this scope.

## Known Limitations

- Auth/authorization, audit writes and media/banner lifecycle await shared providers; this version is not a security-reviewed deployment.
- Single warehouse only, as confirmed by user. No multi-warehouse allocation.
- Public GET returns the definition; current stock/selected-option price is read through evaluation. A preview neither reserves stock nor locks price; it cannot confirm checkout/POS/quotation.
- Same-price rules use current retail prices. A formerly allowed replacement whose price changes is rejected until the definition is repaired; price drift alone does not increment bundle version.
- PUT consumers must adopt dates/groups to preserve configured values; missing fields have documented replacement semantics.
- Phase 3 Sales/cart/reservation/preorder/substitution and full UC-13 AC07/08/11/12 remain unverified/deferred. No deployment or live DB migration performed.
- Pre-existing uncommitted files are preserved. No staging, commit, push, archive edits or Phase 1 handoff edits in this P2 task.
