# WORK-011 — Normalize IAM Package Architecture

## Metadata

- **Work ID**: `WORK-011`
- **Version**: `1.3`
- **Owning Module**: `iam`
- **Risk Reasons**: `authentication`, `authorization`, `security package relocation`, `public DTO skeleton replacement`
- **Design Review**: `PENDING`
- **Decision Owner**: Product Owner / Tech Lead / Security Owner

## 1. Goal and Final Scope

`WORK-011` chỉ là package architecture migration. Công việc được phép gồm move source, cập nhật package declarations/imports/test references, đưa REST DTO về `internal.payload`, chuẩn hóa IAM tree và thay hai unused public DTO skeleton bằng một empty `UserSummaryDto` skeleton.

```text
com.aquasuite.ims.module.iam
├── api/
│   ├── UserExternalService.java
│   ├── dto/UserSummaryDto.java
│   └── event/
└── internal/
    ├── controller/
    ├── service/impl/
    ├── repository/
    ├── entity/
    ├── payload/request/
    ├── payload/response/
    ├── security/
    ├── token/
    ├── error/
    ├── bootstrap/
    └── dev/
```

- Giữ canonical roots `com.aquasuite.ims`, `com.aquasuite.ims.module`, `com.aquasuite.ims.share`, `com.aquasuite.ims.module.iam`; không dùng `com.aquasuite.be`, `features`, `common`.
- Không triển khai user lookup, public service behavior, Spring provider, mapper hoặc feature behavior mới.
- Không sửa HTTP/JSON/business/authentication/authorization/JWT/Redis/schema/data/Flyway/JPA/transaction/locking/concurrency/WORK-010 behavior.
- Không tạo `internal.mapper`, MapStruct, mapper dependency/processor. Existing mapping trong services giữ nguyên.

## 2. Preserved User Skeletons

### `UserExternalService`

Giữ nguyên exact behavior/content ngoài package location không đổi:

```java
package com.aquasuite.ims.module.iam.api;

public class UserExternalService {
}
```

- Empty public class; không interface, method, annotation, Spring bean hoặc reference tới `UserSummaryDto`.

### `UserService`

Chỉ move từ `iam.internal.user.service.UserService` sang `iam.internal.service.UserService` và đổi package declaration:

```java
package com.aquasuite.ims.module.iam.internal.service;

public interface UserService {
}
```

- Empty internal interface; không method/annotation và không expose ngoài IAM.

### `UserServiceImpl`

Chỉ move từ `iam.internal.user.service.impl.UserServiceImpl` sang `iam.internal.service.impl.UserServiceImpl` và đổi package declaration:

```java
package com.aquasuite.ims.module.iam.internal.service.impl;

public class UserServiceImpl {
}
```

- Empty public class; không implement `UserService`/`UserExternalService`, không `@Service`, `@Transactional`, constructor, injected dependency, repository lookup, mapping logic hoặc Spring registration.
- Spring context phải không tạo bean từ ba skeleton này. Provider, method, field, nullability và mapping decisions thuộc feature riêng khi có consumer thực tế.

## 3. Public DTO Skeleton Replacement

Repository-wide inventory xác nhận `UserRequest` và `UserResponse` là empty skeleton, không có production consumer; consumer duy nhất là `IamSensitiveDataBoundaryTest`.

Atomic structural replacement:

- `REMOVE`: `iam.api.dto.request.UserRequest`.
- `REMOVE`: `iam.api.dto.response.UserResponse`.
- `ADD`: `iam.api.dto.UserSummaryDto` với exact skeleton:

```java
package com.aquasuite.ims.module.iam.api.dto;

public class UserSummaryDto {
}
```

`UserSummaryDto` không field, method, constructor tùy biến, nullability contract, Entity mapping, business behavior hoặc Spring/JPA/Jackson annotation. `UserExternalService` không dùng DTO này trong `WORK-011`. Fields và public service methods được quyết định ở work item riêng khi có consumer thực tế. Không compatibility wrapper/deprecated alias vì không có production consumer trong repository; nếu phát hiện external consumer, dừng affected slice và cập nhật design.

## 4. Complete Production Class Mapping

Mọi `MOVE` chỉ đổi location/package/import; annotations, method body, validation/Jackson, mapping và null behavior hiện hữu phải giữ nguyên. Destination chưa tồn tại trừ `KEEP` rows; move phải xóa source cũ atomically.

| Source FQCN | Target FQCN | Action | Boundary | Consumers / Imports | Preservation / Collision Note |
|---|---|---|---|---|---|
| `iam.api.UserExternalService` | same | `KEEP` exact content | Public skeleton | Boundary test | Empty non-bean class; existing file |
| `iam.api.dto.request.UserRequest` | — | `REMOVE` | Obsolete public skeleton | Boundary test only | Empty; atomic removal |
| `iam.api.dto.response.UserResponse` | — | `REMOVE` | Obsolete public skeleton | Boundary test only | Empty; atomic removal |
| — | `iam.api.dto.UserSummaryDto` | `ADD` empty class | Public skeleton | Boundary/structure tests only | No fields/annotations; no collision |
| `iam.internal.auth.controller.AuthController` | `iam.internal.controller.AuthController` | `MOVE` | Internal | Auth service/payload/tests | Preserve endpoints/status/envelope; no collision |
| `iam.internal.auth.dto.request.LoginRequest` | `iam.internal.payload.request.LoginRequest` | `MOVE` | REST payload | Auth controller/service/impl/tests | Preserve validation/trim/JSON/null |
| `iam.internal.auth.dto.request.RefreshRequest` | `iam.internal.payload.request.RefreshRequest` | `MOVE` | REST payload | Auth controller/service/impl/tests | Preserve `refresh_token`/validation |
| `iam.internal.authorization.dto.request.CreatePermissionRequest` | `iam.internal.payload.request.CreatePermissionRequest` | `MOVE` | REST payload | Permission controller/service/impl/tests | Preserve fields/unknown-field rejection |
| `iam.internal.authorization.dto.request.UpdatePermissionRequest` | `iam.internal.payload.request.UpdatePermissionRequest` | `MOVE` | REST payload | Permission controller/service/impl/tests | Preserve optional/null/unknown-field behavior |
| `iam.internal.auth.dto.response.LoginResponse` | `iam.internal.payload.response.LoginResponse` | `MOVE` | REST payload | Auth controller/service/impl/tests | Preserve token/nested-user JSON |
| `iam.internal.auth.dto.response.TokenResponse` | `iam.internal.payload.response.TokenResponse` | `MOVE` | REST payload | Auth controller/service/impl | Preserve JSON/default/null |
| `iam.internal.authorization.dto.response.PermissionPageResponse` | `iam.internal.payload.response.PermissionPageResponse` | `MOVE` | REST payload | Permission controller/service/impl | Preserve paging serialization |
| `iam.internal.authorization.dto.response.PermissionResponse` | `iam.internal.payload.response.PermissionResponse` | `MOVE` | REST payload | Permission/role services/controllers/tests | Preserve fields/service mapping |
| `iam.internal.authorization.dto.response.RoleResponse` | `iam.internal.payload.response.RoleResponse` | `MOVE` | REST payload | Role controller/service/impl | Preserve nested permissions/order |
| `iam.internal.auth.security.AccessAuthenticationConverter` | `iam.internal.security.AccessAuthenticationConverter` | `MOVE` | Internal | Security config/tests | Preserve converter/authority/fail-closed |
| `iam.internal.auth.security.AuthSecurityConfig` | `iam.internal.security.AuthSecurityConfig` | `MOVE` | Internal | Spring context tests | Preserve filter chain/routes/beans |
| `iam.internal.auth.security.AuthSecurityErrorHandlers` | `iam.internal.security.AuthSecurityErrorHandlers` | `MOVE` | Internal | Security components/tests | Preserve 401/403 contract |
| `iam.internal.auth.security.JwtDecoderConfig` | `iam.internal.security.JwtDecoderConfig` | `MOVE` | Internal | Config/token/Redis/tests | Preserve validators/session failure behavior |
| `iam.internal.auth.security.UserDetailsServiceImpl` | `iam.internal.security.UserDetailsServiceImpl` | `MOVE` | Internal | Security config/tests | Preserve lookup/status/authority bean |
| `iam.internal.auth.service.AuthService` | `iam.internal.service.AuthService` | `MOVE` | Internal | Auth controller/impl | Preserve signatures through relocated payloads |
| `iam.internal.auth.service.impl.AuthServiceImpl` | `iam.internal.service.impl.AuthServiceImpl` | `MOVE` | Internal | Auth service/security tests | Preserve transactions/token/session/mapping |
| `iam.internal.authorization.service.impl.CurrentAuthoritiesService` | `iam.internal.service.impl.CurrentAuthoritiesService` | `MOVE` | Internal | Security/auth components/tests | Preserve authority resolution/order/fail-closed |
| `iam.internal.user.service.UserService` | `iam.internal.service.UserService` | `MOVE` only | Internal skeleton | Boundary/structure tests | Preserve empty interface; no collision |
| `iam.internal.user.service.impl.UserServiceImpl` | `iam.internal.service.impl.UserServiceImpl` | `MOVE` only | Internal skeleton | Boundary/structure tests | Preserve empty non-bean class; no collision |
| `iam.internal.auth.token.JwtTokenService` | `iam.internal.token.JwtTokenService` | `MOVE` | Internal | Auth impl/decoder/tests | Preserve claims/algorithm/TTL/errors |
| `iam.internal.auth.token.RedisSessionStore` | `iam.internal.token.RedisSessionStore` | `MOVE` | Internal | Auth impl/decoder/tests | Preserve keys/rotation/outage/fail-closed |
| `iam.internal.authorization.entity.Permission` | `iam.internal.entity.Permission` | `MOVE` | Entity | Repo/services/Role/tests | Preserve JPA mapping/timestamps |
| `iam.internal.authorization.entity.Role` | `iam.internal.entity.Role` | `MOVE` | Entity | User/repo/services/bootstrap/tests | Preserve JPA collections/semantics |
| `iam.internal.user.entity.User` | `iam.internal.entity.User` | `MOVE` | Entity | Repo/auth/security/bootstrap/tests | Preserve JPA/roles/`@PrePersist` |
| `iam.internal.user.enums.UserStatus` | `iam.internal.entity.UserStatus` | `MOVE` | Entity value | User/auth/security/bootstrap/tests | Preserve enum constants; target has no `enums` |
| `iam.internal.authorization.repository.PermissionRepository` | `iam.internal.repository.PermissionRepository` | `MOVE` | Repository | Permission/role services/tests | Preserve locks/queries |
| `iam.internal.authorization.repository.RoleRepository` | `iam.internal.repository.RoleRepository` | `MOVE` | Repository | Role service/bootstrap/tests | Preserve locks/entity graphs/queries |
| `iam.internal.user.repository.UserRepository` | `iam.internal.repository.UserRepository` | `MOVE` | Repository | Auth/security/bootstrap/tests | Preserve all existing methods/entity graphs |
| `iam.internal.user.controller.UserController` | `iam.internal.controller.UserController` | `MOVE` only | Internal skeleton | Boundary test only | Preserve empty/unannotated; no collision |
| `iam.internal.controller.IamPermissionController` | same | `KEEP` | Internal | Update payload imports | Preserve endpoints/security; existing destination |
| `iam.internal.controller.IamRoleController` | same | `KEEP` | Internal | Update response import | Preserve endpoints/security; existing destination |
| `iam.internal.service.IamPermissionService` | same | `KEEP` | Internal | Update payload imports | Preserve signatures; existing destination |
| `iam.internal.service.IamRoleService` | same | `KEEP` | Internal | Update response import | Preserve signatures; existing destination |
| `iam.internal.service.impl.IamPermissionServiceImpl` | same | `KEEP` | Internal | Update entity/repo/payload imports | Preserve transactions/locks/errors/mapping |
| `iam.internal.service.impl.IamRoleServiceImpl` | same | `KEEP` | Internal | Update entity/repo/payload imports | Preserve order/idempotency/locks/mapping |
| `iam.internal.error.AuthErrorCode` | same | `KEEP` | Internal | Relocated classes retain import | Preserve catalog/status |
| `iam.internal.error.IamErrorCode` | same | `KEEP` | Internal | IAM services retain import | Preserve catalog/status |
| `iam.internal.bootstrap.DevelopmentDataInitializer` | same | `KEEP` | Internal | Update entity/repo imports | Preserve profile/conditional seed/rollback |
| `iam.internal.dev.DevAuthController` | same | `KEEP` | Internal skeleton | None | Preserve empty type |

Reconciled action counts: `30 MOVE`, `11 KEEP`, `2 REMOVE`, `1 ADD`, `0 MODIFY`. The `43` existing production types are each handled exactly once; `ADD` is new. No two sources map to one target and no target collides with an unrelated existing class.

## 5. Runtime and Test Safety

- Default scan từ `com.aquasuite.ims` tiếp tục tìm existing controllers/services/config/bootstrap; annotations move cùng annotated classes.
- Spring Data/JPA scans repositories/entities tại target packages. Existing SecurityFilterChain/auth components resolve như trước.
- `UserExternalService`, `UserServiceImpl` và `UserController` phải tiếp tục không là Spring bean; `UserService` vẫn empty interface.
- Bốn request và năm response REST DTO move one-to-one; giữ exact names/types, Lombok/Jackson/validation, constructors/builders/setters, required/optional/null/serialization behavior.
- Move atomic; old/new class không cùng tồn tại. Không duplicate FQCN/simple class/equivalent bean hay missing existing bean.

### Test Migration

- Update all IAM test imports, packages, reflection package strings and source-path references to targets.
- Move auth controller test to `internal.controller`, security-focused tests to `internal.security`, token-focused tests to `internal.token`; keep bootstrap/controller/persistence/service groups otherwise and update imports.
- `IamSensitiveDataBoundaryTest`: remove obsolete ban on `internal.payload`; assert REST-only DTO location, safe public DTOs and no external module import of internal payload. Do not permanently ban future `internal.mapper`.
- Replace stale six-file SHA-256 assertions with semantic structural assertions. Do not delete security boundary coverage.
- Add structural tests asserting: `UserExternalService` remains empty public class without annotations; `UserService` remains empty internal interface; `UserServiceImpl` remains empty public class without annotations/interfaces/constructors declared/dependencies and is not a Spring bean; `UserSummaryDto` is empty public class without annotations; removed DTO classes/files are absent; no production reference to removed DTOs.

## 6. Acceptance Criteria

1. Exact §4 mapping applied with reconciled counts and no missing/duplicate/collision.
2. No production declaration/import/source directory/compiled class under three old namespaces.
3. Three User service skeletons retain exact emptiness/non-bean behavior after permitted package moves.
4. `UserSummaryDto` is an empty public skeleton; two old DTO skeletons and all production dependencies are absent.
5. Nine REST DTO moves preserve JSON/validation/null/error/status behavior.
6. HTTP, business, authentication, authorization, JWT/Redis, schema/data/V1/V2, JPA, transaction/locking/concurrency and WORK-010 behavior unchanged.
7. Spring/JPA/Security startup/discovery remains valid; no duplicate/missing existing bean and no accidental User skeleton bean.
8. Sensitive/package boundary tests reflect the new architecture without weakening security.
9. No `internal.mapper`, MapStruct, mapper dependency/processor or mapping rewrite.
10. Clean full suite, source/output/diff/credential gates pass and user changes remain intact.

## 7. Merge Coordination

1. Implement/review WORK-011 on dedicated branch under a short IAM refactor freeze because conflict risk is high.
2. Merge WORK-011 to shared baseline before other IAM branches.
3. IAM contributors rebase/merge new baseline into their branch, resolve imports/package conflicts there and rerun package plus targeted IAM tests.
4. Do not merge a branch still based on old IAM packages after WORK-011 reaches baseline.

## 8. Verification Gates

Planning state is `NOT_RUN`. Implementation/review uses only `PASS`, `FAIL`, `NOT_RUN`, `NOT_APPLICABLE` with evidence.

| Gate | State | Evidence Required |
|---|---|---|
| Clean compile/full suite | `NOT_RUN` | `.\mvnw.cmd clean test`; counts; no stale `target/` |
| Old packages/directories | `NOT_RUN` | Zero-match source/import scan; old main/test directories absent |
| Clean output | `NOT_RUN` | `target/classes`/`target/test-classes` contain no old-package class |
| Collision/duplicates | `NOT_RUN` | FQCN/simple-name/source/bean inventory |
| Spring/JPA context | `NOT_RUN` | Startup, entity mapping, repository suites |
| Auth/JWT/Redis | `NOT_RUN` | Existing auth, decoder/token, outage/rotation, disabled-user, production-security suites |
| Role/permission | `NOT_RUN` | WORK-010 controller/service/repository/authority/privilege/concurrency suites |
| REST contracts | `NOT_RUN` | Existing HTTP/JSON/validation/error assertions unchanged |
| User skeletons | `NOT_RUN` | Three service skeletons empty/non-bean; `UserSummaryDto` empty; old DTOs/references absent |
| Sensitive boundaries | `NOT_RUN` | Updated sensitive/package/cross-module checks |
| Diff hygiene | `NOT_RUN` | `git diff --check`; no behavior/POM/migration drift |
| Credential scan | `NOT_RUN` | Report result without displaying values |

## 9. Superseded Findings and Open Decisions

- Provider-wiring finding and later AOP/proxy verification finding are `SUPERSEDED` by the owner’s final scope decision. They are not implemented or partially retained: WORK-011 has no provider, lookup method, Optional result, transaction, injection, Entity-to-DTO mapping or provider/AOP tests.
- No architecture decision remains open for the reduced scope. A future feature with a real consumer must separately design fields/nullability/methods/provider/security/transaction/mapping for the public User API.

