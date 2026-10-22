# SCRUM08 / UC-01 - Implementation Handoff

IMPLEMENTATION_STATUS: READY_FOR_REVIEW
PLAN_ID: SCRUM08-UC01
PLAN_VERSION: 4
PLAN_SHA256: 36627f8b3da19fbb303c073bbba9276514c7bb5a44210134277fdb34773561ca
SPEC_VERSION: 0.4.0
SPEC_SHA256: b47fa98475044b6750be5d91bb8dcece934f15f4d252588abe4954c9f131aafc
IMPLEMENTATION_AUTHORIZATION: DIRECT_USER_INSTRUCTION
INDEPENDENT_PLAN_REVIEW: NOT_PERFORMED
CODE_REVIEW: PENDING
FINAL_GATE: NOT_PERFORMED
VERIFIED_AT: 2026-10-20T22:02:23+07:00

## Lanes Completed

- Entry checks: Git dirty baseline captured/preserved; direct user implementation authorization recorded separately. Plan v4 records the user's explicit confirmation to create a linked customers profile; no independent approval fabricated.
- Persistence: V6 full_name + non-null phone uniqueness with non-destructive preflight; UUID and V1-V6 unchanged; V7 adds customers with unique UUID user_id and RESTRICT FK.
- Registration/validation: required fields, password policy/UTF-8 byte limit/confirmation/terms, duplicate credentials, pending CUSTOMER account.
- OTP/activation: existing Redis TTL and mail infrastructure, 6-digit OTP BCrypt hash, 300-second expiry, 60-second resend cooldown, 5 wrong attempts, version-aware atomic consume, serialized verify/resend.
- Customer profile: after OTP, CRM profile creation and ACTIVE status commit together; RETAIL/ACTIVE, CUS-UUID, name/phone/email, nullable address/company; no guessed backfill.
- Authentication: same existing JWT/session issuer for login and post-activation; no registration token/verifier/config/job.
- Verification/docs: registration tests, full backend clean verify, runbook/code guide and this handoff completed.

## Files Changed

All entries belong to repository BE. Paths are repository-relative. Existing SCRUM14 dirty changes are excluded from this change set and were preserved.

| Repository | Relative Path |
|---|---|
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/registration/controller/RegisterController.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/registration/service/RegisterService.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/registration/payload/RegisterRequest.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/registration/payload/RegisterResponse.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/registration/payload/VerifyRegistrationRequest.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/registration/payload/ResendRegistrationOtpRequest.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/registration/validation/ValidRegistration.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/registration/validation/RegistrationValidator.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/registration/error/RegistrationErrorCode.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/auth/security/AuthSecurityConfig.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/auth/service/AuthService.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/auth/service/impl/AuthServiceImpl.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/user/entity/User.java |
| BE | src/main/java/com/aquasuite/ims/module/iam/internal/user/repository/UserRepository.java |
| BE | src/main/resources/db/migration/V6__SCRUM08_user_registration.sql |
| BE | src/test/java/com/aquasuite/ims/module/iam/internal/registration/RegisterValidationTest.java |
| BE | src/test/java/com/aquasuite/ims/module/iam/internal/registration/RegisterApiIntegrationTest.java |
| BE | src/test/java/com/aquasuite/ims/module/iam/internal/registration/RegisterOtpIntegrationTest.java |
| BE | src/test/java/com/aquasuite/ims/module/iam/internal/registration/RegisterConcurrencyIT.java |
| BE | src/test/java/com/aquasuite/ims/module/iam/internal/registration/RegisterMigrationIT.java |
| BE | src/test/java/com/aquasuite/ims/module/iam/internal/auth/AuthApiIntegrationTest.java |
| BE | src/test/java/com/aquasuite/ims/module/iam/internal/auth/AuthProductionSecurityTest.java |
| BE | src/test/java/com/aquasuite/ims/module/iam/internal/persistence/IamMigrationIntegrationTest.java |
| BE | src/test/java/com/aquasuite/ims/support/FlywayMigrationIT.java |
| BE | src/test/java/com/aquasuite/ims/integration/AuthenticationUpgradeIT.java |
| BE | docs/README.md |
| BE | docs/migrations/flyway.md |
| BE | docs/SCRUM08-UC01-code-guide.md |
| BE | .agent/plans/active/SCRUM08-UC01.md |
| BE | .agent/reviews/SCRUM08-UC01-implementation-authorization.md |
| BE | .agent/handoffs/SCRUM08-UC01.md |

| BE | src/main/resources/db/migration/V7__SCRUM08_customer_profiles.sql |
| BE | src/main/java/com/aquasuite/ims/module/crm/api/CustomerRegistrationApi.java |
| BE | src/main/java/com/aquasuite/ims/module/crm/api/dto/CustomerRegistrationRequest.java |
| BE | src/main/java/com/aquasuite/ims/module/crm/api/dto/CustomerRegistrationResponse.java |
| BE | src/main/java/com/aquasuite/ims/module/crm/internal/entity/Customer.java |
| BE | src/main/java/com/aquasuite/ims/module/crm/internal/repository/CustomerRepository.java |
| BE | src/main/java/com/aquasuite/ims/module/crm/internal/service/impl/CustomerRegistrationServiceImpl.java |
| BE | src/test/java/com/aquasuite/ims/testsupport/PostgreSqlIntegrationTestSupport.java |
| BE | src/test/java/com/aquasuite/ims/module/iam/internal/registration/CustomerProfileIntegrationTest.java |
| BE | docs/specifications/SCRUM08-UC01-register-customer-account.md |
| BE | .agent/reviews/SCRUM08-UC01-customer-profile-confirmation.md |

## Implementation Summary

Register validates and creates one UUID PENDING_ACTIVATION User with CUSTOMER; password and OTP are BCrypt hashes. A PostgreSQL atomic INSERT ON CONFLICT DO NOTHING handles duplicate races without producing failed SQL that logs password hashes; role assignment is in the same transaction.

Verification consumes the current OTP, creates the CRM customers profile through CustomerRegistrationApi, commits profile and ACTIVE status together, then delegates session issuance to AuthService. Login and verification share a private helper in AuthServiceImpl; JwtTokenService and RedisSessionStore bytes remain unchanged. Resend/verify lock the same pending user row. No cross-feature internal imports or JPA relationships were added.

CRM creation is MANDATORY in the caller transaction, idempotent for the same user/payload and conflict-safe for changed data. A failure after the profile insert rolls back profile and activation together; resend/verification recovers. No profile exists before OTP activation.

Pending users survive mail failure and can resend. OTP consumption followed by DB rollback requires resend. Activation committed but session failed returns REG_009; ordinary login is the recovery path. Verification replay cannot create a second session.

## Tests Added / Updated

Customer account/profile coverage: 18 Surefire + 6 Failsafe = 24 tests. Includes field boundaries, secret redaction in validation logs, pending/active lifecycle, privilege injection, TTL/leading-zero/cooldown/attempts, mail/Redis/DB/session failures, duplicate and verify/resend races, fresh/upgrade/preflight migrations.

AuthApiIntegrationTest adds an ACTIVE-status guard test for the reused issuer. Production security tests verify exact public method/path exceptions. Existing schema tests retain strict assertions updated for full_name/phone uniqueness; migration count tests reflect V6, including V2-to-V6 adoption. No foundation skeleton hash assertion was removed or weakened. CustomerProfileIntegrationTest verifies atomic rollback/recovery, MANDATORY transaction, idempotency/conflict and module boundaries; migration coverage includes V6->V7 without backfill and FK enforcement. PostgreSqlIntegrationTestSupport cleans only disposable customer fixtures before account fixtures to preserve production FK RESTRICT.

## Verification Performed

| Check | Result / evidence |
|---|---|
| Baseline full verify before implementation | PASS: 186 Surefire + 65 Failsafe = 251; local ignored scrum08-baseline-verify.log |
| Production compile | PASS |
| Focused registration + auth | PASS: 35 tests in initial targeted run |
| Focused customer API/profile | PASS: 8 tests, including CRM rollback and contract/boundary checks; scrum08-customer-focused.log |
| Final ./mvnw.cmd clean -Pphase1-it verify | PASS: 205 Surefire + 71 Failsafe = 276; 0 failures, 0 errors, 0 skipped |
| Final timestamp | 2026-10-20T22:02:23+07:00; BUILD SUCCESS, 03:22 min |
| Report evidence | target/surefire-reports/TEST-*.xml and target/failsafe-reports/TEST-*.xml; parsed and totals asserted |
| Final full log | scrum08-customer-full-verify.log, ignored local log; PowerShell redirects UTF-16 |
| Protected baseline hashes | PASS: 36 unchanged protected/dirty-baseline paths, including SCRUM14 files, JWT/session, V1-V5 and governance |
| Module boundary scan | PASS: IAM/CRM boundary integration test passes; IAM consumes only CRM public API/DTOs |
| git diff --check | PASS |
| Real SMTP / live database migration | NOT_RUN: tests use mock mail and disposable PostgreSQL/Redis containers |

Earlier intermediate failures were corrected: managed role collection handling after atomic INSERT; stale schema/phone and migration-count assertions. The final clean verify above is the evidence for the delivered source, not those intermediate results.

## Contract Verification

| Spec AC | Evidence / result |
|---|---|
| AC-01 | RegisterValidationTest + API invalid/malformed request checks; field-specific errors and rejected secrets absent from captured logs: PASS |
| AC-02 | UUID/fullName/CUSTOMER/BCrypt/PENDING_ACTIVATION persisted; pending login rejected: PASS |
| AC-03 | Duplicate email ignore-case/phone rejected; simultaneous registration creates one User; no pending overwrite: PASS |
| AC-04 | Stored TTL asserted near 300 seconds; real Redis expiry tested with shortened TTL; resend cooldown/rotation and leading-zero OTP checked: PASS |
| AC-05 | Wrong attempts persist despite DB rollback; 5 attempts block; concurrent verification and resend serialize: PASS |
| AC-06 | ACTIVE status and usable existing JWT/session after verify; pending/inactive/locked issuer calls rejected: PASS |
| AC-07 | Mail/Redis/DB/session fault injection and recovery paths: PASS |
| AC-08 | Exact three POST exceptions, forged privilege fields ignored, old auth/security regression and credential log checks: PASS |
| AC-09 | Fresh/upgrade V6/V7, no guessed backfill, FK and original migration/skeleton preservation: PASS |
| AC-10 | customers UUID/user_id/name/phone/email/RETAIL/ACTIVE/code/timestamps; absent before OTP; exactly one on concurrent verification: PASS |
| AC-11 | CRM failure after insert rolls back both profile and activation; resend recovery, MANDATORY, idempotency/conflict and module boundaries: PASS |

HTTP paths and statuses match spec 0.4.0: register 201, verify 200 with existing LoginResponse snake_case tokens, resend 200. No JWT is issued before OTP activation, no registrationToken/channel/provider endpoint added.

## Deviations From Approved Plan

Plan v4/spec 0.4.0 implement the explicitly confirmed customer profile addition. HTTP/token contracts are unchanged. V7, public CRM contracts, customer tests and fixture cleanup are listed in the plan amendment. Direct user authorization is recorded; independent Plan Review/Code Review and Final Gate are not asserted as complete.

## Known Limitations

- Pending users remain in PostgreSQL; no cleanup job by explicit user direction.
- Customer projects/address book/customer care, activity audit, Google/SMS, terms persistence and broad IP/contact rate limiting remain deferred. The basic customers profile is implemented.
- Real SMTP credentials/configuration and an active CUSTOMER role must already be provisioned by the owner-approved deployment process. No real email or production migration was executed.
- Existing noncanonical/duplicate phone data blocks V6 for manual reconciliation; no data is silently modified.
- Independent review remains pending. No stage, commit, push or deployment was performed.

API details: [code guide](../../docs/SCRUM08-UC01-code-guide.md). Business scope: [spec](../../docs/specifications/SCRUM08-UC01-register-customer-account.md). Execution steps: [active plan](../plans/active/SCRUM08-UC01.md).

Historical verification before the customer profile addition was 272 tests at 20:37:41 +07. The 276-test clean run above replaces that evidence for the current customer-account implementation.
