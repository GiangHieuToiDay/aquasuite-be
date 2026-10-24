# FEATURE-007 IAM Restructure Change Specification

SPEC_ID: FEATURE-007-IAM-RESTRUCTURE-SCOPE-CHANGE
SPEC_VERSION: 0.4.1-proposed
SPEC_STATUS: PROPOSED
IMPLEMENTATION_AUTHORIZED: NO
PARENT_FEATURE: FEATURE-007
PARENT_SPEC_VERSION: 0.3.1
PARENT_PLAN_ID: FEATURE-007
PARENT_PLAN_VERSION: 2
PROJECT_ROOT: D:\DO_AN\Aquasuite\aquasuite-be
SCOPE: BACKEND_ONLY

## 1. Overview and Scope

Đặc tả này materialize scope change đã được Human xác nhận cho FEATURE-007. Phạm vi là IAM mechanical restructuring trước khi tiếp tục Authentication implementation.

Mục tiêu:

- Chuẩn hóa package của `module/iam/api` và `module/iam/internal`.
- Tách DTO request/response.
- Đặt service interface trực tiếp trong `service/` và implementation trong `service/impl/`.
- Đổi tên các JPA entity theo kiến trúc đích.
- Giữ nguyên behavior, HTTP contract, database schema, JWT, Redis và Authentication behavior.

Phase này không triển khai Authentication V2, không sửa business logic và không thay đổi FEATURE-007 Plan v2 tại thời điểm Spec Review.

## 2. Current Architecture

Git repository duy nhất trong phạm vi là `BE`, tại `D:\DO_AN\Aquasuite\aquasuite-be`. Sibling frontend không thuộc scope.

Các vùng source hiện tại:

```text
com.aquasuite.ims.module.iam.api
com.aquasuite.ims.module.iam.api.dto
com.aquasuite.ims.module.iam.internal.controller
com.aquasuite.ims.module.iam.internal.dto
com.aquasuite.ims.module.iam.internal.entity
com.aquasuite.ims.module.iam.internal.enums
com.aquasuite.ims.module.iam.internal.repository
com.aquasuite.ims.module.iam.internal.security
com.aquasuite.ims.module.iam.internal.service
com.aquasuite.ims.module.iam.internal.service.Impl
com.aquasuite.ims.module.iam.internal.session
com.aquasuite.ims.module.iam.internal.error
```

Evidence source chính:

- `src/main/java/com/aquasuite/ims/module/iam/internal/controller/AuthController.java:1-43`
- `src/main/java/com/aquasuite/ims/module/iam/internal/security/AuthSecurityConfig.java:1-56`
- `src/main/java/com/aquasuite/ims/module/iam/internal/entity/UserEntity.java:25-76`
- `src/main/java/com/aquasuite/ims/module/iam/internal/entity/RoleEntity.java:22-60`
- `src/main/java/com/aquasuite/ims/module/iam/internal/entity/PermissionEntity.java:15-38`
- `src/main/java/com/aquasuite/ims/module/iam/internal/repository/UserRepository.java:1-26`
- `src/main/java/com/aquasuite/ims/module/iam/internal/service/AuthService.java:1-17`
- `src/main/java/com/aquasuite/ims/module/iam/internal/service/AuthServiceImpl.java:1-32`

## 3. Target IAM Package Architecture

Kiến trúc đích bắt buộc:

```text
com.aquasuite.ims.module.iam
├── api
│   ├── UserExternalService.java
│   └── dto
│       ├── request
│       │   └── UserRequest.java
│       └── response
│           └── UserResponse.java
└── internal
    ├── auth
    │   ├── controller
    │   ├── dto
    │   │   ├── request
    │   │   └── response
    │   ├── security
    │   ├── service
    │   │   └── impl
    │   └── token
    ├── user
    │   ├── controller
    │   ├── entity
    │   ├── enums
    │   ├── repository
    │   └── service
    │       └── impl
    ├── authorization
    │   ├── entity
    │   ├── repository
    │   └── service
    └── error
```

`AuthService` là interface tại `auth/service/`; `AuthServiceImpl` là implementation tại `auth/service/impl/`. Không tạo interface hoặc implementation mới cho các skeleton nếu source hiện tại không yêu cầu.

## 4. Exact Production/Test File Matrix

### Production Source

| Current Path | Target Path | Change Type |
|---|---|---|
| `src/main/java/com/aquasuite/ims/module/iam/api/UserExternalService.java` | Giữ nguyên | Protected unchanged |
| `src/main/java/com/aquasuite/ims/module/iam/api/dto/UserRequest.java` | `src/main/java/com/aquasuite/ims/module/iam/api/dto/request/UserRequest.java` | Move/package |
| `src/main/java/com/aquasuite/ims/module/iam/api/dto/UserResponse.java` | `src/main/java/com/aquasuite/ims/module/iam/api/dto/response/UserResponse.java` | Move/package |
| `src/main/java/com/aquasuite/ims/module/iam/internal/controller/AuthController.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/controller/AuthController.java` | Move/package/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/dto/LoginRequest.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/dto/request/LoginRequest.java` | Move/package |
| `src/main/java/com/aquasuite/ims/module/iam/internal/dto/RefreshRequest.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/dto/request/RefreshRequest.java` | Move/package |
| `src/main/java/com/aquasuite/ims/module/iam/internal/dto/LoginResponse.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/dto/response/LoginResponse.java` | Move/package |
| `src/main/java/com/aquasuite/ims/module/iam/internal/dto/TokenResponse.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/dto/response/TokenResponse.java` | Move/package |
| `src/main/java/com/aquasuite/ims/module/iam/internal/service/AuthService.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/service/AuthService.java` | Move/package/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/service/AuthServiceImpl.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/service/impl/AuthServiceImpl.java` | Move/package/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/security/AccessAuthenticationConverter.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/security/AccessAuthenticationConverter.java` | Move/package/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/security/AuthSecurityConfig.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/security/AuthSecurityConfig.java` | Move/package/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/security/AuthSecurityErrorHandlers.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/security/AuthSecurityErrorHandlers.java` | Move/package/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/security/JwtTokenService.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/token/JwtTokenService.java` | Move/package/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/security/CurrentAuthoritiesService.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/authorization/service/CurrentAuthoritiesService.java` | Move/package/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/session/RedisSessionStore.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/token/RedisSessionStore.java` | Move/package/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/controller/UserController.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/user/controller/UserController.java` | Protected supersede |
| `src/main/java/com/aquasuite/ims/module/iam/internal/service/UserService.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/user/service/UserService.java` | Protected supersede |
| `src/main/java/com/aquasuite/ims/module/iam/internal/service/Impl/UserServiceImpl.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/user/service/impl/UserServiceImpl.java` | Protected supersede |
| `src/main/java/com/aquasuite/ims/module/iam/internal/entity/UserEntity.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/user/entity/User.java` | Rename/move |
| `src/main/java/com/aquasuite/ims/module/iam/internal/entity/RoleEntity.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/authorization/entity/Role.java` | Rename/move |
| `src/main/java/com/aquasuite/ims/module/iam/internal/entity/PermissionEntity.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/authorization/entity/Permission.java` | Rename/move |
| `src/main/java/com/aquasuite/ims/module/iam/internal/enums/UserStatus.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/user/enums/UserStatus.java` | Move/package |
| `src/main/java/com/aquasuite/ims/module/iam/internal/repository/UserRepository.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/user/repository/UserRepository.java` | Move/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/repository/RoleRepository.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/authorization/repository/RoleRepository.java` | Move/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/repository/PermissionRepository.java` | `src/main/java/com/aquasuite/ims/module/iam/internal/authorization/repository/PermissionRepository.java` | Move/import |
| `src/main/java/com/aquasuite/ims/module/iam/internal/error/AuthErrorCode.java` | Giữ nguyên | No change |
| `src/main/resources/redis/*` | Giữ nguyên | No change |

### Test Source

Các file sau giữ nguyên test intent; chỉ cập nhật package/import/type/path assertion cần thiết:

| Test Path | Expected Update |
|---|---|
| `src/test/java/com/aquasuite/ims/module/iam/internal/auth/AuthApiIntegrationTest.java` | DTO, entity, service/token imports |
| `src/test/java/com/aquasuite/ims/module/iam/internal/auth/AuthorityMappingTest.java` | `RoleEntity`/`PermissionEntity` → `Role`/`Permission` |
| `src/test/java/com/aquasuite/ims/module/iam/internal/auth/AuthProductionSecurityTest.java` | Security package references nếu có |
| `src/test/java/com/aquasuite/ims/module/iam/internal/auth/CurrentAuthorityIntegrationTest.java` | Entity and authorization service imports |
| `src/test/java/com/aquasuite/ims/module/iam/internal/auth/JwtSecurityIntegrationTest.java` | Entity and security imports |
| `src/test/java/com/aquasuite/ims/module/iam/internal/auth/JwtTokenServiceTest.java` | Token package imports nếu có |
| `src/test/java/com/aquasuite/ims/module/iam/internal/auth/RedisRotationIntegrationTest.java` | Entity/service/token imports |
| `src/test/java/com/aquasuite/ims/module/iam/internal/persistence/IamEntityMappingIntegrationTest.java` | Entity imports and mapping assertions |
| `src/test/java/com/aquasuite/ims/module/iam/internal/persistence/IamMigrationIntegrationTest.java` | Entity/repository imports nếu có |
| `src/test/java/com/aquasuite/ims/module/iam/internal/persistence/IamRepositoryIntegrationTest.java` | Entity/repository imports and generic types |
| `src/test/java/com/aquasuite/ims/module/iam/internal/persistence/IamSensitiveDataBoundaryTest.java` | Public DTO imports and source path assertions |
| `src/test/java/com/aquasuite/ims/testsupport/AuthIntegrationTestSupport.java` | IAM imports nếu có |

## 5. Protected FEATURE-006 Supersede Scope

| Protected File | Pre-refactor SHA-256 | Decision |
|---|---|---|
| `src/main/java/com/aquasuite/ims/module/iam/api/UserExternalService.java` | `CA15B1007A1A1DC8715AF5A39017CADEBD9664659B17FCBC1FFD963D95E7E96B` | `PROTECTED_UNCHANGED` |
| `src/main/java/com/aquasuite/ims/module/iam/api/dto/UserRequest.java` | `8E48FEFC28471A3AF46550B3A0D2AEECEA1EB896B7E4CDEC302CCFC925BEFEA9` | Proposed supersede/move |
| `src/main/java/com/aquasuite/ims/module/iam/api/dto/UserResponse.java` | `932B8CA8A7EFBF29A46B9FC4EB6BB2BBCFD208E83E936C20F50040444C2C42C4` | Proposed supersede/move |
| `src/main/java/com/aquasuite/ims/module/iam/internal/controller/UserController.java` | `BE71FCA446B74A27ADB82685B78DE9ABAF5DB608B8DFF6A50717D6295E84699C` | Proposed supersede/move |
| `src/main/java/com/aquasuite/ims/module/iam/internal/service/UserService.java` | `561594BC97BD9A4FB225FBBC16E01B6E048FE1516B3C5A66827C03686A632AC2` | Proposed supersede/move |
| `src/main/java/com/aquasuite/ims/module/iam/internal/service/Impl/UserServiceImpl.java` | `B0DEDBF5D90A43EEAE07BE1991F277914008DBF80C1042186BA944A7A617A2BB` | Proposed supersede/move |

Post-refactor verification:

- `UserExternalService.java` phải giữ nguyên SHA-256.
- Năm file di chuyển được phép có post-hash mới do package declaration, import và approved type-reference changes.
- Diff phải chứng minh không đổi logic, method signatures ngoài thay đổi package/type bắt buộc, annotations, HTTP behavior hoặc schema mapping.
- Archived FEATURE-006 artifacts không được sửa.

## 6. Public Java API Compatibility

HTTP wire contract không thay đổi. Tuy nhiên, public Java package change là breaking change đối với consumer import trực tiếp:

```text
com.aquasuite.ims.module.iam.api.dto.UserRequest
→ com.aquasuite.ims.module.iam.api.dto.request.UserRequest

com.aquasuite.ims.module.iam.api.dto.UserResponse
→ com.aquasuite.ims.module.iam.api.dto.response.UserResponse
```

Compatibility strategy:

- Repository-wide import scan trước và sau refactor.
- Cập nhật test boundary/path assertions.
- Không tạo wrapper hoặc duplicate DTO nếu source hiện tại không yêu cầu.
- Ghi rõ Java consumer impact trong implementation handoff.
- Product Owner/Lead Architect phải chấp thuận public Java package change trước Plan Revision.

## 7. JPA and Repository Compatibility

Entity rename:

- `UserEntity` → `User`.
- `RoleEntity` → `Role`.
- `PermissionEntity` → `Permission`.

Phải giữ nguyên `@Entity`, `@Table`, schema `public`, column names, field types, `@PrePersist`, `FetchType.LAZY`, join tables `user_roles` và `role_permissions`.

Repository compatibility phải giữ nguyên method names, return semantics và `JpaRepository` generic types sau khi cập nhật entity type.

JPQL verification:

- Scan toàn bộ `@Query`, JPQL string và entity-name references.
- Kiểm tra entity-name resolution sau rename.
- Không thay đổi query semantics.
- Chạy Spring context và JPA integration test để phát hiện metamodel/query failure.

`@EntityGraph(attributePaths = {"roles", "roles.permissions"})` phải giữ nguyên semantics.

## 8. Spring Security and Redis Invariants

Không được thay đổi:

- `SecurityFilterChain`, route matchers và stateless policy.
- JWT signing/validation, claims, issuer, audience, token type và expiry.
- Authentication entry point và access denied handler.
- Current user status và authority loading.
- Redis key names, TTL, rotation, replay detection và logout revocation.
- Redis scripts tại `src/main/resources/redis/`.
- Login, refresh, logout và protected request behavior.

Sau refactor phải xác minh Spring beans:

- `SecurityFilterChain`.
- `PasswordEncoder`.
- JWT token/decoder service.
- `AuthService` và `AuthServiceImpl`.
- `RedisSessionStore`.
- `CurrentAuthoritiesService`.

## 9. Dirty Git Baseline, Ownership and Rollback Mapping

Dirty baseline trước khi materialize artifact gồm các thay đổi tại `.env.example`, `pom.xml`, `UserRepository.java`, application configuration, FEATURE-007 specification/review files, Authentication source/test/Redis resources và test support. Các thay đổi này thuộc user và không được reset, clean, checkout, commit hoặc push.

File ownership:

| Lane | Ownership |
|---|---|
| `BE-IAM-PACKAGE` | IAM production package moves, entity/repository rename, imports |
| `BE-IAM-TEST` | IAM test imports, path assertions và regression checks |
| `BE-IAM-VERIFY` | SHA-256, Git status, context/JPA/bean/security verification |

Rollback mapping:

- Nếu compile/context fail: dừng lane, không xóa dirty baseline.
- Nếu JPA/repository fail: revert package/type changes trong approved change set, không rollback schema.
- Nếu security/Redis regression fail: dừng trước Authentication continuation, giữ nguyên source baseline để review.
- Rollback phải ghi post-rollback Git status và SHA-256.

## 10. Functional and Non-functional Acceptance Criteria

### Functional Criteria

- Compile thành công.
- Spring context khởi động thành công.
- IAM service/repository/security beans được scan đúng.
- JPA entities map đúng database schema hiện tại.
- Repository finder và EntityGraph behavior không đổi.
- Login, refresh, logout, JWT validation, authorization và Redis rotation regression pass.
- HTTP endpoints, JSON contract và status/error behavior không đổi.

### Non-functional Criteria

- Không còn package `service.Impl`.
- Không còn duplicate old/new class.
- Không còn stale imports hoặc JPQL entity names.
- Không import private `internal` package từ module khác.
- Không sửa archived artifacts.
- Không ghi secret, password, token hoặc private key vào artifact/source.
- Pre/post SHA-256 verification hoàn tất.

## 11. Regression Test Requirements

Phải chạy và lưu evidence cho:

1. Compile và Spring context load.
2. JPA entity mapping integration test.
3. Flyway/migration compatibility test.
4. Repository integration test.
5. Service bean wiring test.
6. Auth API integration test.
7. JWT invalid/expiry/type validation tests.
8. Security filter chain regression.
9. User status, role and permission authority tests.
10. Redis login/refresh/logout rotation tests.
11. Refresh race, replay detection và Redis failure tests.
12. Public DTO boundary test.
13. Full Maven regression suite.

## 12. Refactor Sequencing

1. Capture Git status, diff summary và protected SHA-256 baseline.
2. Move package và rename type theo matrix.
3. Update imports, type references, repository generics, EntityGraph references và test paths.
4. Verify no old production paths/classes remain.
5. Run compile, Spring context, JPA/repository and bean tests.
6. Run security/JWT/Redis regression tests.
7. Produce implementation handoff.
8. Chỉ sau khi refactor được Code Review/Final Gate xử lý mới tiếp tục Authentication implementation theo revised plan.

## 13. Out of Scope

- Authentication V2.
- JWT redesign hoặc token claim change.
- Redis state-machine redesign.
- OAuth2 provider integration.
- HTTP endpoint/JSON contract change.
- Database migration/schema change.
- Business logic implementation cho UserController/UserService/UserServiceImpl.
- Sửa archived FEATURE-006 artifacts.
- Tạo FEATURE-008 active plan.
- Stage, commit, push, reset, clean hoặc archive.

## 14. Dependencies and Approval Gates

Dependencies:

- Human scope decision `FEATURE-007-IAM-RESTRUCTURE-SCOPE-CHANGE`.
- FEATURE-007 approved Spec v0.3.1.
- FEATURE-007 approved Plan v2.
- FEATURE-006 protected baseline.
- `AGENTS.md` và `docs/workflow/feature-delivery.md`.

Approval gates:

1. Product Owner hoặc Lead Architect phê duyệt Specification này.
2. CODEX_REVIEW xác minh revision preconditions.
3. CODEX_PLAN lập revised active plan cùng `PLAN_ID: FEATURE-007`, tăng version đúng một lần.
4. CODEX_REVIEW phê duyệt revised plan và SHA-256 mới.
5. Chỉ sau Plan Review approval mới được sửa source.
6. Code Review và Final Gate phải xác minh toàn bộ acceptance criteria.

```text
SPEC_STATUS: PROPOSED
IMPLEMENTATION_AUTHORIZED: NO
SPEC_REVIEW: PENDING
PLAN_REVISION_STATUS: NOT_STARTED
SOURCE_CHANGE_STATUS: NOT_STARTED
```
