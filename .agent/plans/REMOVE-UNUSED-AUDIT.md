# Remove unused Java audit module

PLAN_ID: REMOVE-UNUSED-AUDIT
PLAN_VERSION: 1
AUTHORIZATION: DIRECT_USER_INSTRUCTION
PLAN_REVIEW: NOT_PERFORMED
IMPLEMENTATION_STATUS: READY_FOR_REVIEW
VERIFICATION: PASS_275_TESTS

User requested "bỏ audit" after the scope was explained: remove the unused Java
module, retain audit_entries and existing migrations. No runtime business caller
of AuditApi was found. This does not claim independent review approval or replace
the active registration plan.

## File scope

- Delete the five Java files under src/main/java/com/aquasuite/ims/module/audit/.
- Delete src/test/java/com/aquasuite/ims/module/audit/internal/service/AuditServiceIT.java
  (two tests of the removed provider).
- Remove audit imports, entity/repository scan packages and bean registration from
  src/test/java/com/aquasuite/ims/support/BundleCoreTestSupport.java.
- Remove AuditService from the scope list in
  src/test/java/com/aquasuite/ims/architecture/BundleCoreBoundaryTest.java.
- Update current-state documentation: docs/README.md,
  docs/architecture/Architecture-Aquasuite.md,
  docs/specs/SCRUM14-UC13-product-combos.md (revision 0.13.0),
  docs/SCRUM14-UC13-phase1-code-guide.md.
- Write .agent/handoffs/REMOVE-UNUSED-AUDIT.md after verification.

## Preserved behavior

Keep V1-V7 byte-for-byte, database schema and historical audit rows. Retain SQL-only
migration/upgrade assertions and fixture cleanup for audit_entries. Do not modify
catalog/registration/authentication production logic, events, Inventory, Bruno
collection, existing user changes or archived workflow artifacts. No live SQL.

## Verification

Run mvn -o clean -Pphase1-it verify using isolated Testcontainers databases.
Confirm no module.audit references in active Java source or compiled output;
confirm no migration diff and review whitespace. No stage/commit/push.
