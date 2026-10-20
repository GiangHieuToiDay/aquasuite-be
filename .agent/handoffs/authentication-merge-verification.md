# Authentication branch integration verification

IMPLEMENTATION_STATUS: READY_FOR_REVIEW
PLAN_ID: SCRUM14-UC13
PLAN_VERSION: 0.11.0
SPEC_VERSION: 0.11.0
MERGE_STATUS: RESOLVED_NOT_COMMITTED
SOURCE: origin/SCRUM-1-user-login-and-authentication at e365cad
TARGET: feature/SCRUM-14-phase-1 at f9f8bc5 plus preserved uncommitted Phase 1/2 changes
AUTHORIZATION: User requested merge, chose incoming UUID authentication and JWT on other APIs, and explicitly authorized continuation after credential disclosure.
CODE_REVIEW: PENDING
FINAL_GATE: NOT_GRANTED

## Lanes Completed

- Preserved dirty worktree in a temporary ZIP and retained stash; fetched with Windows schannel TLS verification, no TLS bypass.
- Merged incoming branch with --no-commit --no-ff and reapplied existing Phase 1/2 work. Resolved POM, environment/profile, architecture and application test conflicts.
- Unified on incoming User/Role/Permission UUID authentication. Removed duplicate legacy User/repository/encoder, old hardcoded credential initializer and mock SecurityUtils/fallback identity.
- Reused the existing IamFacade contract under iam/api; inventory/audit scalar actor IDs and relevant DTOs now use UUID. Password change reads the verified JWT subject, not the username or a default numeric ID.
- Preserved password change/recovery features using the incoming account/hash and a separate recovery table. Reset does not reactivate a disabled account.
- Preserved V1/V2/V3 and introduced V4 UUID IAM integration plus V5 recovery state. Verified old data and orphan actor mapping without dropping original account records or guessing role grants.
- Kept incoming security allowlist exactly POST login/refresh. Other APIs, including storefront routes, require JWT/current ACTIVE user/current Redis session.
- Full verification, source checks and documentation completed. Existing repository governance and archived artifacts preserved.

## Files Changed

The merged tree includes pre-existing Phase 1/2 changes as requested; the following staged paths are not all newly authored in the Auth integration. .agent plan/handoff remain unstaged by governance. Existing Phase 1/2 handoffs are preserved.

| Repository | Relative Path |
|---|---|
| BE | `.env.example` |
| BE | `bruno/AquaSuite-Bruno/Authentication/01-Login.bru` |
| BE | `bruno/AquaSuite-Bruno/Authentication/02-Refresh-Token.bru` |
| BE | `bruno/AquaSuite-Bruno/Authentication/03-Logout.bru` |
| BE | `bruno/AquaSuite-Bruno/Authentication/DevTestAuth.bru` |
| BE | `bruno/AquaSuite-Bruno/README.md` |
| BE | `bruno/AquaSuite-Bruno/bruno.json` |
| BE | `bruno/AquaSuite-Bruno/environments/Local.bru` |
| BE | `docker-compose.yaml` |
| BE | `docs/README.md` |
| BE | `docs/SCRUM14-UC13-phase1-code-guide.md` |
| BE | `docs/architecture/Architecture-Aquasuite.md` |
| BE | `docs/architecture/module-contracts.md` |
| BE | `docs/configuration/spring-profiles.md` |
| BE | `docs/migrations/SCRUM14-UC13-phase1.md` |
| BE | `docs/migrations/flyway.md` |
| BE | `docs/specifications/FEATURE-006-iam-foundation.md` |
| BE | `docs/specifications/FEATURE-007-authentication-foundation.md` |
| BE | `docs/specifications/FEATURE-007-iam-restructure-change.md` |
| BE | `docs/specifications/reviews/FEATURE-006-v0.3.2-approval.md` |
| BE | `docs/specifications/reviews/FEATURE-006-v0.4.0-approval.md` |
| BE | `docs/specifications/reviews/FEATURE-006-v0.4.0-d9734040-technical-review.md` |
| BE | `docs/specifications/reviews/FEATURE-006-v0.4.0-ffb94669-technical-review.md` |
| BE | `docs/specifications/reviews/FEATURE-006-v0.4.0-technical-review.md` |
| BE | `docs/specifications/reviews/FEATURE-007-iam-restructure-v0.4.1-human-approval.md` |
| BE | `docs/specifications/reviews/FEATURE-007-v0.3.1-approval.md` |
| BE | `docs/specs/SCRUM14-UC13-product-combos.md` |
| BE | `docs/templates/feature-spec.md` |
| BE | `docs/workflow/feature-delivery.md` |
| BE | `pom.xml` |
| BE | `scripts/db/Apply-Uc13Phase1.ps1` |
| BE | `src/main/java/com/aquasuite/ims/module/audit/api/dto/AuditCommand.java` |
| BE | `src/main/java/com/aquasuite/ims/module/audit/internal/entity/AuditEntry.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/controller/BundleAdminController.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/controller/BundleSkuController.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/controller/BundleStorefrontController.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/entity/BundleComponentGroup.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/entity/BundleConfiguration.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/mapper/BundleCoreMapper.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleComponentGroupRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreCreateRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreDetail.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreMutationResult.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreStatusRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreSummary.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreUpdateRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleEvaluation.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleEvaluationRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleItemRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleJsonDeserializers.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundlePage.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleSkuResponse.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleVersionRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleAdminService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleDetailAssembler.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundlePricingService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleSkuQueryService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleStorefrontService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/IamFacade.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/api/IamFacade.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/api/UserExternalService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/api/dto/UserSummaryDto.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/api/dto/request/UserRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/api/dto/response/UserResponse.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/events/PasswordChangedEvent.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/controller/AuthController.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/dto/request/LoginRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/dto/request/RefreshRequest.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/dto/response/LoginResponse.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/dto/response/TokenResponse.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/security/AccessAuthenticationConverter.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/security/AuthSecurityConfig.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/security/AuthSecurityErrorHandlers.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/security/JwtDecoderConfig.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/security/UserDetailsServiceImpl.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/service/AuthService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/service/impl/AuthServiceImpl.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/token/JwtTokenService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/token/RedisSessionStore.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/authorization/entity/Permission.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/authorization/entity/Role.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/authorization/repository/PermissionRepository.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/authorization/repository/RoleRepository.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/authorization/service/impl/CurrentAuthoritiesService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/bootstrap/DevelopmentDataInitializer.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/config/IamDataInitializer.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/dev/DevAuthController.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/entity/User.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/entity/UserPasswordRecovery.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/entity/enums/UserStatus.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/error/AuthErrorCode.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/repository/UserPasswordRecoveryRepository.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/repository/UserRepository.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/security/SecurityUtils.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/service/impl/IamFacadeImpl.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/service/impl/PasswordServiceImpl.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/user/controller/UserController.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/user/entity/User.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/user/enums/UserStatus.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/user/repository/UserRepository.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/user/service/UserService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/iam/internal/user/service/impl/UserServiceImpl.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/api/InventoryAvailabilityApi.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/dto/StockAdjustCommand.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/internal/controller/InventoryController.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/internal/controller/StockCountController.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/internal/entity/StockCount.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/internal/entity/StockMovement.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/internal/payload/res/StockCountResponse.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/internal/service/InventoryService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/internal/service/StockCountService.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/internal/service/impl/InventoryServiceImpl.java` |
| BE | `src/main/java/com/aquasuite/ims/module/inventory/internal/service/impl/StockCountServiceImpl.java` |
| BE | `src/main/java/com/aquasuite/ims/share/config/password/PasswordEncoderConfig.java` |
| BE | `src/main/resources/application-dev.yml` |
| BE | `src/main/resources/application-prod.yml` |
| BE | `src/main/resources/application-test.yml` |
| BE | `src/main/resources/application.yml` |
| BE | `src/main/resources/db/manual/uc13-phase1-preflight.sql` |
| BE | `src/main/resources/db/manual/uc13-phase1-verify.sql` |
| BE | `src/main/resources/db/migration/V1__Initial_legacy_schema.sql` |
| BE | `src/main/resources/db/migration/V2__SCRUM14_phase1_combo_core.sql` |
| BE | `src/main/resources/db/migration/V3__SCRUM14_phase2_combo_options.sql` |
| BE | `src/main/resources/db/migration/V4__integrate_uuid_authentication.sql` |
| BE | `src/main/resources/db/migration/V5__preserve_password_recovery.sql` |
| BE | `src/main/resources/redis/revoke-session.lua` |
| BE | `src/main/resources/redis/rotate-refresh.lua` |
| BE | `src/test/java/com/aquasuite/ims/architecture/BundleCoreBoundaryTest.java` |
| BE | `src/test/java/com/aquasuite/ims/integration/AuthenticationMergeIT.java` |
| BE | `src/test/java/com/aquasuite/ims/integration/AuthenticationUpgradeIT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/audit/internal/service/AuditServiceIT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/controller/BundleAdminControllerTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/migration/BundleCoreMigrationIT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleAdminServiceIT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleCoreHttpIT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleDetailAssemblerTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundlePhase2IT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleStorefrontServiceTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/AuthApiIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/AuthProductionSecurityTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/AuthorityMappingTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/CurrentAuthorityIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/DisabledUserIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/JwtDecoderConfigTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/JwtSecurityIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/JwtTokenServiceTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/PrivilegeBoundaryIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/RedisOutageIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/RedisRotationIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/auth/UserDetailsServiceImplTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/bootstrap/DevelopmentDataInitializerRollbackIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/bootstrap/DevelopmentDataInitializerTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/persistence/IamEntityMappingIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/persistence/IamMigrationIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/persistence/IamRepositoryIntegrationTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/persistence/IamSensitiveDataBoundaryTest.java` |
| BE | `src/test/java/com/aquasuite/ims/module/iam/internal/service/PasswordServiceTest.java` |
| BE | `src/test/java/com/aquasuite/ims/support/BundleCoreTestSupport.java` |
| BE | `src/test/java/com/aquasuite/ims/support/FlywayMigrationIT.java` |
| BE | `src/test/java/com/aquasuite/ims/testsupport/AuthIntegrationTestSupport.java` |
| BE | `src/test/java/com/aquasuite/ims/testsupport/PostgreSqlIntegrationTestSupport.java` |
| BE | `src/test/resources/db/uc13-core-baseline.sql` |
| BE | `.agent/plans/SCRUM14-UC13.md` |
| BE | `.agent/handoffs/authentication-merge-verification.md` |

## Implementation Summary

Reuse authentication v2 JWT decoder, current-authority converter, login/refresh/logout and Redis session/revocation implementation. No parallel verifier, identity fallback, generic adapter layer or Catalog-specific auth bypass. Existing combo pricing/MapStruct/CRUD/options and Inventory batch read remain intact behind the shared security filter.

IAM identity is UUID throughout User, current-user public contract, password event, inventory mutation/count/movement and audit command/entity. Password recovery hashes, expiry and rate-limit state are separate from the incoming User schema. Legacy OTP reset no longer overrides INACTIVE/LOCKED status owned by Auth. New development seeding is environment-driven and disabled by default in the integrated configuration; no hardcoded seeded password remains.

V4 archives complete legacy account rows in iam_legacy_users and preserves a deterministic numeric-to-UUID mapping in iam_legacy_user_ids, including orphan historical actor IDs. Account hashes/profile values and actor references are retained. Missing email/password identities are locked, original values archived; case-insensitive identity conflicts abort atomically. No old numeric role IDs are silently promoted to new role grants. V5 preserves recovery state. V1/V2/V3 were compared with the pre-merge backup and their original bytes retained.

## Tests Added / Updated

- AuthenticationMergeIT: 7 complete-application tests using actual login JWTs, PostgreSQL and Redis. No token -> 401 across non-auth route families; valid access JWT -> combo/admin/evaluation; refresh/malformed tokens rejected; logout revokes Catalog/Inventory access; inventory stores JWT UUID actor; password change updates the same UUID account/hash; disabled user cannot reuse an earlier token.
- AuthenticationUpgradeIT: 3 tests: populated V3->V5 account/profile/hash/recovery/audit/count/movement/combo revision preservation and retry; whole-migration rollback on normalized identity conflict; incomplete accounts retained but locked.
- Original Auth/Redis/JWT/role/seed/repository tests retained. IAM catalog assertions are scoped to the five IAM tables/four join-table FKs in the merged database; IAM migration history assertion now targets V4. The synthetic partial-migration rehearsal remains version 1.
- Missing-JWT-secret startup assertion loads JwtTokenService without unrelated database-backed business beans. Public DTO/entity boundary coverage now includes the actual current-user API. Protected skeleton fingerprints compare canonical LF text; the six source skeletons match the incoming branch independent of Windows checkout endings.
- Existing password unit coverage migrated to UUID/new passwordHash/recovery storage. Core fixtures use UUID accounts and audit IDs. Core business HTTP tests deliberately isolate filters; AuthenticationMergeIT separately proves production security with the complete application.
- Test-only core configuration is excluded from scanning the real application, avoiding accidental mock actors/repository scan restrictions in production-context tests.

## Verification Performed

- PASS: `./mvnw.cmd -Pphase1-it verify`, exit 0, BUILD SUCCESS at 2026-10-20 17:56:28 +07; 2m57s.
- PASS: 186 Surefire + 65 Failsafe = 251 tests; zero failures/errors/skipped. Totals independently summed from XML reports. Surefire includes incoming DB/Redis integration tests, so 186 is not a pure unit-test count.
- PASS: AuthenticationMergeIT 7/7; AuthenticationUpgradeIT 3/3; all original Phase 1/2 regression tests and incoming authentication suite.
- PASS: earlier clean build removed stale deleted User/initializer/encoder classes; final rerun corrected only test cleanup of product_variants, not runtime behavior. Final post-test source edits only clarify storefront JavaDoc.
- PASS: one User entity source, no legacy mock SecurityUtils/old initializer, no duplicate direct POM dependencies, no unresolved Git conflicts, no stage of AGENTS.md or .agent, original Phase 1 handoff unchanged.
- PASS: staged diff whitespace check excluding the byte-preserved pre-existing V1 legacy schema. The default full check reports six trailing spaces already present in that V1; they are intentionally retained rather than modifying migration bytes/checksum for cosmetics.
- Evidence: ignored local auth-merge-final-verify.log; target/surefire-reports and target/failsafe-reports XML.
- NOT_RUN: live/shared database migration, deployment, independent review/Final Gate and external credential rotation. No commit, push or history rewrite.

Earlier verification attempts exposed test-fixture/schema assumptions: duplicate/stale legacy classes before clean, a core test configuration imported into the real app, foundation-only schema/payload assertions, mixed CRLF/LF fingerprint baselines, and missing SKU cleanup between full application tests. These were corrected; no skipped tests or disabled security filters in the complete-app JWT coverage.

## Contract Verification

- POST /api/v1/auth/login and /refresh remain the only unauthenticated allowlist. Every other API requires Bearer access JWT, including Catalog storefront/admin/evaluation, Inventory and password forgot/reset. Refresh tokens cannot authorize these endpoints. Revoked/invalid tokens and disabled users are rejected by the incoming shared Auth implementation.
- Incoming policy is authentication on non-auth APIs; this merge does not introduce a new Catalog permission matrix or claim owner-only method authorization.
- UUID is the actor identity in Java API/DTO/storage. No client-supplied numeric actor or mock fallback. New IamFacade contract stays in iam/api and does not expose entities; no cross-module JPA links added.
- Combo request/price/version/options/lifecycle behavior preserved, now behind JWT. Business Audit writes/media/Sales remain deferred, while existing audit actor storage has been migrated to UUID.
- ApiResponse and existing business errors retained; global Auth error handlers handle authentication failures.
- V1/V2/V3 preserved. Incoming standalone IAM V1 DDL is integrated forward as V4, avoiding duplicate Flyway version 1. Existing branch-specific IAM-only databases require explicit reviewed onboarding, not automatic baseline/repair.

## Deviations From Approved Plan

Implementation was directly authorized by the user; no independent/hash-matched 0.11.0 approval is claimed. The incoming Auth branch is adopted for identity/security, while the existing repository governance is retained as specified by the current AGENTS.md. The incoming foundation-only tests were adapted to the merged runtime without dropping their sensitive-data/identity constraints. One imported spec received only EOF whitespace normalization; incoming approval artifacts are historical and are not approvals of this integrated revision.

## Known Limitations

- Merge is resolved and staged but still awaits a human merge commit; HEAD remains f9f8bc5 and MERGE_HEAD e365cad. No push. Human decides staging of .agent artifacts; they are intentionally excluded.
- Retained backup: C:/Users/quang/AppData/Local/Temp/aquasuite-before-auth-merge-yi2b05xv/worktree.zip; retained safety stash is named safety before authentication merge 2026-10-20. Do not blindly reapply it onto the already restored code.
- No live migration. Rehearse/back up data first, especially databases initialized from the standalone Auth branch (different V1). Identity collisions abort for manual reconciliation. Legacy created_at is interpreted as UTC; complete raw legacy rows remain available for reconciliation.
- Old numeric role/permission metadata is archived without guessed grants; assign new roles through the IAM ownership process.
- JWT_SECRET, PostgreSQL/Redis and SMTP configuration must come from deployment environment. Development seed needs explicit opt-in/environment password. Existing credential exposure was disclosed; initializer removed. External revocation/history remediation remains human-owned and was not performed.
- Authenticated non-auth API access follows incoming policy; finer business permissions, new audit write integration, media and Phase 3 remain separate work. The so-called public/storefront route names no longer imply anonymous access.
