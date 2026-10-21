# Catalog: api / internal package alignment

PLAN_ID: CATALOG-API-INTERNAL
PLAN_VERSION: 3
PLAN_STATUS: IMPLEMENTED
PLAN_REVIEW: NOT_PERFORMED
IMPLEMENTATION_STATUS: READY_FOR_REVIEW

## Purpose

Align catalog with `docs/architecture/Architecture-Aquasuite.md` and
`docs/architecture/module-contracts.md`, using `com.aquasuite.ims` as confirmed
by the user. Preserve all existing uncommitted combo simplifications.

User narrowed the request: leave the existing event untouched and only add the
api folder. The latest user instruction explicitly requires it to remain empty;
package-info.java added in revision 2 is removed. This supersedes the event
migration proposed in revision 1. Existing uncommitted combo changes are preserved.

## Exact changes

1. Keep `src/main/java/com/aquasuite/ims/module/catalog/api/` empty; do not add
   package-info.java, placeholder files, interfaces or DTOs.
2. Preserve events, event imports, Inventory, REST payloads and internal packages.

## Verification

- Verify the directory exists and contains no files; check diff whitespace.
- No runtime logic changed; no new tests required. Git does not track empty directories.
- No stage/commit/push.

## Authorization boundary

User explicitly authorized this reduced scope. There is no event migration or
consumer contract change. This record does not replace the active registration
plan or claim independent Plan Review approval.
