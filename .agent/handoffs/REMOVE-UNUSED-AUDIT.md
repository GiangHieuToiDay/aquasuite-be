# Remove unused Java audit module

IMPLEMENTATION_STATUS: READY_FOR_REVIEW
PLAN_ID: REMOVE-UNUSED-AUDIT
PLAN_VERSION: 1
AUTHORIZATION: DIRECT_USER_INSTRUCTION
CODE_REVIEW: NOT_PERFORMED
FINAL_GATE: NOT_PERFORMED

## Lanes Completed

- Removed the five unused Java provider files and their two dedicated integration tests.
- Removed test-context bean/entity/repository registrations and the obsolete boundary-test name.
- Updated current architecture, spec 0.13.0, code guide and documentation index.
- Full clean verification passed.

## Files Changed

Repository: aquasuite-be. Relative paths:

- src/main/java/com/aquasuite/ims/module/audit/api/AuditApi.java (deleted)
- src/main/java/com/aquasuite/ims/module/audit/api/dto/AuditCommand.java (deleted)
- src/main/java/com/aquasuite/ims/module/audit/internal/entity/AuditEntry.java (deleted)
- src/main/java/com/aquasuite/ims/module/audit/internal/repository/AuditEntryRepository.java (deleted)
- src/main/java/com/aquasuite/ims/module/audit/internal/service/AuditService.java (deleted)
- src/test/java/com/aquasuite/ims/module/audit/internal/service/AuditServiceIT.java (deleted)
- src/test/java/com/aquasuite/ims/support/BundleCoreTestSupport.java
- src/test/java/com/aquasuite/ims/architecture/BundleCoreBoundaryTest.java
- docs/architecture/Architecture-Aquasuite.md
- docs/specs/SCRUM14-UC13-product-combos.md
- docs/SCRUM14-UC13-phase1-code-guide.md
- docs/README.md
- .agent/plans/REMOVE-UNUSED-AUDIT.md
- .agent/handoffs/REMOVE-UNUSED-AUDIT.md

## Implementation Summary

There was no business caller of AuditApi. The Java module and its registrations
are removed; no replacement provider, stub, feature flag or dependency was added.
Empty source/test audit directories were removed after confirming they contained
no remaining files and resolved within the workspace.

## Tests Added / Updated

The two AuditServiceIT tests were removed with their provider. Existing migration,
UUID upgrade and SQL-only audit_entries fixture/assertions remain to verify legacy
schema compatibility. No implementation-mirroring tests were added for deletion.

## Verification Performed

- PASS: mvn -o clean -Pphase1-it verify, BUILD SUCCESS, 2026-10-21.
  205 Surefire + 70 Failsafe = 275 tests, zero failures/errors/skipped, isolated
  Docker Testcontainers. The two fewer tests are the deleted AuditServiceIT cases.
- PASS: no AuditApi/AuditCommand/AuditEntry/AuditService/module.audit references in
  active production or test Java source.
- PASS: no migration file changes; no live database commands.
- PASS: git diff --check (existing line-ending advisory only).

## Contract Verification

No HTTP contract, combo transaction/version behavior, register/login flow, event or
Inventory implementation changed. audit_entries and V1-V7 remain unchanged.
Architecture/spec distinguish the future FE-02 requirement from current behavior.

## Deviations From Approved Plan

No independent approval is claimed. Scope follows direct user authorization and
the scoped plan; no technical deviations. Earlier uncommitted source/documentation
changes and user environment/template edits are preserved. Archived artifacts are untouched.

## Known Limitations

The application has no business audit provider. Existing audit rows remain in the
database; the historical migrations still create/upgrade the table on fresh installs.
No stage, commit, push, live migration or deployment was performed.
