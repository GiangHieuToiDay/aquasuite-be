# Combo response simplification

PLAN_ID: COMBO-RESPONSE-SIMPLIFICATION
PLAN_VERSION: 1
IMPLEMENTATION_STATUS: READY_FOR_REVIEW
VERIFICATION: PASS_277_TESTS
PLAN_REVIEW: NOT_PERFORMED
AUTHORIZATION: DIRECT_USER_INSTRUCTION

User explicitly selected the proposed response simplification: combine detail and
summary into BundleResponse, return full response on status change, and return only
a success envelope on delete. This authorizes the HTTP response change; it does not
claim independent review approval or supersede the active registration plan.

## Scope

- Replace BundleCoreDetail and BundleCoreSummary with BundleResponse, preserving
  all former detail fields, nested Item, dates and component groups.
- Remove BundleCoreMutationResult and mapper summary/mutation methods.
- Update catalog controllers, services, assembler and mapper. Remove the unused
  mapper dependency from BundleStorefrontService.
- Keep request DTOs, validation, transaction/locking/version rules, evaluation,
  events, Inventory and empty catalog/api directory unchanged.
- Update catalog tests and type references in BundleCoreTestSupport and
  AuthenticationMergeIT. Update the UC13 spec and code guide for new responses.

## Contract

- Lists: ApiResponse<BundlePage<BundleResponse>>, including items and definition.
- GET/create/update/status/publish/unpublish: full BundleResponse inside ApiResponse;
  POST creation retains HTTP 201 and Location.
- DELETE: HTTP 200 ApiResponse<Void>, success message, data omitted. Soft delete,
  stale-version rejection and current-version no-op retry remain unchanged.
- DELETE no longer returns the post-delete version; callers remove the row after
  success. Retrying with the pre-delete version still returns 409 as before.

## Verification

- Clean build and existing unit/integration tests, including HTTP shape, lifecycle,
  no-op and concurrency assertions. No live database operations.
- Confirm removed DTO names and mapper methods are absent from active Java source.
- Review diff, preserve existing changes, no stage/commit/push.
