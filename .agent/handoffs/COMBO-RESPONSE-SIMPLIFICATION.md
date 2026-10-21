# Combo response simplification handoff

IMPLEMENTATION_STATUS: READY_FOR_REVIEW
PLAN_ID: COMBO-RESPONSE-SIMPLIFICATION
PLAN_VERSION: 1
AUTHORIZATION: DIRECT_USER_INSTRUCTION
CODE_REVIEW: NOT_PERFORMED
FINAL_GATE: NOT_PERFORMED

## Lanes Completed

- Unified the detail/summary response and removed mutation response and redundant mappings.
- Updated admin/storefront callers and relevant tests; retained the previous ponytail changes.
- Updated UC13 response specification (0.12.0), code guide and documentation index.
- Clean verification passed; no independent approval is claimed.

## Files Changed

Repository: aquasuite-be. Paths relative to repository root.

- `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleResponse.java` (renamed from BundleCoreDetail)
- `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreSummary.java` (removed)
- `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreMutationResult.java` (removed)
- `src/main/java/com/aquasuite/ims/module/catalog/internal/controller/BundleAdminController.java`
- `src/main/java/com/aquasuite/ims/module/catalog/internal/controller/BundleStorefrontController.java`
- `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleAdminService.java`
- `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleStorefrontService.java`
- `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleDetailAssembler.java`
- `src/main/java/com/aquasuite/ims/module/catalog/internal/mapper/BundleCoreMapper.java`
- `src/test/java/com/aquasuite/ims/support/BundleCoreTestSupport.java`
- `src/test/java/com/aquasuite/ims/integration/AuthenticationMergeIT.java` (response type reference only)
- `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleCoreHttpIT.java`
- `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleDeletionIT.java`
- `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleStatusIT.java`
- `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleDetailAssemblerTest.java`
- `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleStorefrontServiceTest.java`
- `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundlePhase2IT.java` (response type reference only)
- `docs/specs/SCRUM14-UC13-product-combos.md`
- `docs/SCRUM14-UC13-phase1-code-guide.md`
- `docs/README.md`
- `.agent/plans/COMBO-RESPONSE-SIMPLIFICATION.md`
- `.agent/handoffs/COMBO-RESPONSE-SIMPLIFICATION.md`

## Implementation Summary

List/get/create/update/status/publish/unpublish use the complete BundleResponse.
DELETE service returns void; HTTP still returns 200 with the normal ApiResponse
envelope and success message, without data. Mapper summary/mutation methods and
the now-unused storefront mapper dependency are removed. No SQL migration.

## Tests Added / Updated

- HTTP assertions cover full list/status responses and absent DELETE data.
- Deletion assertions inspect persisted soft-delete status and confirm current-version
  no-op retry does not bump version; stale retry remains rejected.
- Response/mapper references and storefront constructor fixture are updated.
- Existing version, rollback and concurrent-write tests remain in place.
- Earlier uncommitted multi-bundle isolation test is preserved.

## Verification Performed

- PASS: `mvn -o -DskipTests compile`.
- PASS: `mvn -o clean -Pphase1-it verify` on 2026-10-21, BUILD SUCCESS,
  205 Surefire + 72 Failsafe = 277 tests, zero failures/errors/skipped.
  Docker Testcontainers used isolated databases. Reports: target/surefire-reports
  and target/failsafe-reports.
- The first full run failed one newly added exact timestamp assertion because
  PostgreSQL rounds timestamps to microseconds. The assertion now checks all
  other response fields exactly and updatedAt within one microsecond; no-op
  is compared with the persisted response. The complete clean rerun passed.
- PASS: no old DTO names or mapper summary/mutation references in active Java source.
- PASS: `git diff --check` (line-ending advisory only).

## Contract Verification

GET/create/update retain the former detail fields; list/status now contain those
same fields. POST create retains 201/Location. DELETE retains 200 and its success
message but no longer returns data/version. Request validation, expectedVersion,
transaction boundaries, soft deletion and evaluation contracts are unchanged.

## Deviations From Approved Plan

No independent plan approval exists. Implementation follows the direct user instruction
and scoped plan; no technical scope deviations. Existing user formatting changes in
BundleCoreUpdateRequest are preserved and are not part of this refactor.

## Known Limitations

- Frontend/client code outside this repository has not been modified or verified;
  callers must stop reading DELETE data. Lists now have larger JSON payloads.
- DELETE with the pre-delete version still returns 409 on retry; the response no
  longer exposes the post-delete version. This is documented rather than silently
  changing the concurrency contract.
- Events, Inventory, registration and the empty catalog/api folder are untouched.
- No stage, commit, push, live migration or deployment.
