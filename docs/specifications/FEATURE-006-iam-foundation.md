# IAM Foundation Feature Specification

SPEC_ID: FEATURE-006
SPEC_VERSION: 0.4.0
SPEC_STATUS: DRAFT_FOR_REVIEW

## Metadata and Governance

- **Feature ID**: `FEATURE-006`
- **Feature Name**: `IAM Foundation`
- **Version**: `0.4.0`
- **Owner / Author**: Product Owner / IAM team — chưa được chỉ định
- **Owning Module**: `iam`
- **Repository Scope**: `BE (aquasuite-be)`
- **Project Root**: `D:\DO_AN\Aquasuite\aquasuite-be`
- **Target Package**: `com.aquasuite.ims.module.iam`
- **Architecture Boundary**: `api/` chứa public contract; `internal/` chứa persistence và implementation riêng của module
- **Runtime Baseline**: Java 21, Spring Boot 3.5.16
- **Database Baseline**: PostgreSQL
- **Migration Tool**: Flyway
- **Spec Review Status**: `PENDING`
- **Spec Review Approver**: Product Owner / Lead Architect — chưa được chỉ định
- **Latest Review Verdict**: `APPROVED` chỉ đối với historical version `0.3.2`, reviewed content SHA-256 `8585ba4c40d77adf769f5efc9619069cd6deb2c4e848aba1f8632f95d92f9338`; verdict này không áp dụng cho revision `0.4.0`
- **Spec Review Evidence**: `docs/specifications/reviews/FEATURE-006-v0.3.2-approval.md` được giữ nguyên làm historical evidence. Product Owner đã yêu cầu architecture revision; revision `0.4.0` đang chờ independent Spec Review mới.
- **Risk Level**: `HIGH`, vì đặc tả định hình dữ liệu danh tính, password hash và quan hệ phân quyền nền tảng
- **Current Workflow Evidence**: Tại thời điểm revision `0.4.0`, canonical spec vẫn là file này. Active plan version `2`, approval của plan đó và implementation handoff đều dựa trên Spec `0.3.2` nên đã stale đối với architecture revision này. Implementation hiện đã bắt đầu; revision này không cấp quyền sửa source, migration hoặc tests cho đến khi Spec Review mới và Plan version `3` được duyệt.

### Approved Product Decisions

Các quyết định dưới đây đã được Product Owner chốt; chúng không đồng nghĩa với việc Spec Review đã được phê duyệt.

**Previously approved:**

- `users` chỉ quản lý tài khoản nhân viên nội bộ; không có customer account trong `FEATURE-006`.
- Primary key của `users`, `roles` và `permissions` dùng UUID.
- `user_roles` dùng composite primary key `(user_id, role_id)`; `role_permissions` dùng composite primary key `(role_id, permission_id)`.
- Không lưu `role_id` hoặc `permission_id` trực tiếp trong `users`; mọi association đi qua join table.

**Approved in version 0.3.0 and retained unless superseded below:**

1. **Provider identity**: Hoãn `provider` và `provider_id`; baseline migration không tạo hai cột này.
2. **Verification data**: Hoãn toàn bộ verification fields sang Activation Feature; baseline migration không tạo các cột này.
3. **FCM and device data**: Hoãn `fcm_token` và device model sang Notification Feature; baseline migration không tạo các cột/bảng này.
4. **Phone**: `phone` nullable và chưa unique.
5. **Avatar**: `avatar_url` nullable, lưu URL/path.
6. **User status**: `UserStatus` gồm chính xác `PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`, `LOCKED`; database default là `PENDING_ACTIVATION`. Business state transitions chưa được triển khai trong feature này.
7. **Assignment metadata (superseded in version 0.4.0)**: Product Owner đã quyết định `user_roles` chỉ còn `user_id`, `role_id`; bỏ `assigned_at` và `assigned_by` để dùng pure many-to-many mapping. Quyết định này thay thế assignment-actor decision của version `0.3.2`.
8. **Database namespace**: Các bảng foundation nằm trong PostgreSQL schema `public`.
9. **UUID generation**: Application tạo UUID trước khi persist; database không cấp UUID mặc định.
10. **Timestamp ownership**: Các trường timestamp dùng PostgreSQL `TIMESTAMPTZ`, biểu diễn instant UTC; database cấp giá trị cho các cột `created_at`.
11. **Integration-test strategy**: Ưu tiên Testcontainers PostgreSQL và phải kiểm tra Docker/CI support trước implementation. Nếu không được hỗ trợ, dùng PostgreSQL thật cô lập do CI cung cấp; H2 không phải fallback tương đương.
12. **Existing IAM skeleton**: Giữ public contract hiện tại. Chỉ sửa file cần thiết được đăng ký trong approved implementation plan.

**Approved architecture revision for version 0.4.0:**

13. **Entity inventory**: Chỉ có ba JPA entities trong `iam.internal.entity`: `UserEntity`, `RoleEntity`, `PermissionEntity`; không có join entity hoặc embedded ID class.
14. **Relationship mapping**: `UserEntity` sở hữu unidirectional `roles` qua `user_roles`; `RoleEntity` sở hữu unidirectional `permissions` qua `role_permissions`. Cả hai dùng `@ManyToMany(fetch = LAZY)` và không dùng cascade `ALL`, `REMOVE` hoặc `PERSIST`.
15. **Enum package**: `UserStatus` nằm trong `com.aquasuite.ims.module.iam.internal.enums`.

### Revision Change Summary

So với approved content `0.3.2`, revision `0.4.0` thay explicit join-entity model bằng hai pure unidirectional many-to-many mappings, bỏ assignment metadata khỏi `user_roles`, giảm entity/repository inventory, và cố định package enum riêng. Các quyết định UUID, password hash, timestamps, uniqueness, phone/avatar, deferred provider/verification/FCM fields, PostgreSQL/Flyway, protected skeletons và out-of-scope authentication vẫn giữ nguyên. Approval evidence `0.3.2` không bị sửa nhưng không phê duyệt nội dung mới này.

## 1. Business Context and Objectives

### Problem Statement

**Current implementation based on Spec v0.3.2:** Implementation đã bắt đầu. Repository hiện có `V1__create_iam_foundation.sql`, các entity chính, join entity/embedded-ID classes, Spring Data repositories và PostgreSQL integration tests được tạo theo approved architecture v0.3.2. Migration V1 hiện vẫn chứa assignment metadata của `user_roles`. Chưa có evidence đầy đủ để xác nhận V1 đã được áp dụng tại database/environment nào cần bảo toàn; sự tồn tại của migration file hoặc test execution không được diễn giải thành kết luận về production/dev/staging Flyway history.

**Target architecture in Spec v0.4.0:** Revision này thay implementation model v0.3.2 bằng đúng ba JPA entities, hai unidirectional lazy many-to-many associations, `UserStatus` trong `iam.internal.enums`, và join tables không có entity/embedded ID hoặc assignment metadata. Existing implementation work phải được bảo toàn cho đến khi Spec v0.4.0 được review và Plan v3 đăng ký refactor/migration path. Trước khi quyết định sửa V1 hay tạo forward migration, Plan v3 bắt buộc kiểm tra `flyway_schema_history` và schema catalog trên mọi environment cần bảo toàn; không reset database hoặc sửa Flyway history.

### Business Goals and Scope

- Quản lý duy nhất tài khoản nhân viên nội bộ; không tạo customer account.
- Chuẩn hóa các bảng `users`, `roles`, `permissions`, `user_roles` và `role_permissions` trên PostgreSQL.
- Quản lý quan hệ user–role và role–permission qua bảng liên kết; không đặt `role_id` hoặc `permission_id` trực tiếp trong `users`.
- Thiết lập Flyway làm nguồn sự thật cho schema và cấu hình JPA chỉ kiểm tra schema, không tự sinh schema production.
- Định nghĩa đúng ba entity, enum package, repository tối thiểu và hai unidirectional many-to-many mappings phục vụ persistence foundation; không tạo production DTO/mapper khi chưa có consumer thực tế.
- Kiểm chứng migration, constraints, mappings và repository bằng integration tests thực sự chạy trên PostgreSQL.

### Foundation Boundary

`FEATURE-006` triển khai duy nhất persistence foundation: schema/migration được quản lý bằng Flyway, JPA entities và relationships nội bộ, Spring Data repositories tối thiểu, mapping persistence cần thiết và integration tests PostgreSQL. Feature này không cung cấp luồng đăng nhập, xác thực credential, phát hành/kiểm tra token, activation, state-transition service, authorization decision, RBAC enforcement, HTTP endpoint hoặc public inter-module operation mới. Các logic authentication/authorization đó phải được đặc tả và phê duyệt trong feature sau.

### Out of Scope

- Login, JWT, refresh token và Redis.
- Spring Security configuration.
- OAuth2, Google login và customer registration.
- RBAC enforcement và controller endpoints.
- Gửi email, activation và reset-password processing.
- Public Java service contract cho authentication/authorization và domain events.
- Seed tài khoản quản trị, role hoặc permission production.
- Sửa IAM skeleton, source, `pom.xml`, migration hoặc application profiles trong giai đoạn tạo spec.

## 2. Actors, Permissions, and Security Model

### Actor and Authorization Matrix

| Actor | Identity Context | Operation in This Feature | Authorization State |
|---|---|---|---|
| Nhân viên nội bộ | Tài khoản thuộc bảng `users` | Được biểu diễn trong persistence model | Enforcement ngoài phạm vi |
| Quản trị viên IAM | Chưa có authentication contract trong feature này | Quản lý account/role/permission trong feature tương lai | Endpoint và enforcement ngoài phạm vi |
| Migration process | Flyway dưới application startup hoặc deployment job | Tạo/rollback-forward schema theo migration đã duyệt | Credential do môi trường triển khai cung cấp |
| Test process | Profile `test` | Chạy migration và repository integration tests trên PostgreSQL cô lập | Không dùng credential production |

### Data Protection and Sensitive Fields

- Không lưu password dạng rõ. Cột đầu vào `password` được chuẩn hóa thành `password_hash`; thuật toán tạo hash thuộc feature authentication sau, nhưng database chỉ nhận chuỗi hash đã tạo.
- `password_hash`, `verification_code`, datasource password và token không được trả qua DTO, log, exception message hoặc `toString()`.
- `fcm_token` và device model không tồn tại trong baseline. Notification Feature tương lai phải tự đặc tả data protection, lifecycle thu hồi/xoay vòng và DTO/log policy trước khi lưu bất kỳ device credential nào.
- `email` và `phone` là dữ liệu cá nhân; chỉ lưu khi có mục đích nghiệp vụ, chuẩn hóa trước khi kiểm tra unique, và không xuất hiện trong log không cần thiết.
- Không hardcode database credentials trong YAML, source, migration, test report hoặc tài liệu.

## 3. Detailed Business Rules and Logic

### Core Business Rules

1. `users` chỉ đại diện tài khoản nhân viên nội bộ.
2. Mỗi `username` và mỗi `email` đã chuẩn hóa phải định danh duy nhất một user. Case-insensitive uniqueness và whitespace normalization là hai invariant riêng: unique index trên `lower(...)` chỉ xử lý khác biệt hoa/thường; `CHECK (value = btrim(value))` từ chối leading/trailing whitespace. Application phải trim trước persistence trong feature tạo/cập nhật account tương lai; foundation không triển khai account service. Internal whitespace không bị thay đổi bởi foundation.
3. `password_hash` bắt buộc và chỉ chứa password hash; feature này không định nghĩa cơ chế login hay thuật toán hash.
4. Một user có thể có nhiều role và một role có thể gán cho nhiều user qua `user_roles`.
5. Một role có thể có nhiều permission và một permission có thể thuộc nhiều role qua `role_permissions`.
6. Không lưu `role_id` hay `permission_id` trong `users` khi đã có bảng liên kết.
7. `role_name` và `permission.code` là business key duy nhất, không phân biệt hoa thường theo unique index đề xuất.
8. Một cặp user–role và một cặp role–permission chỉ tồn tại tối đa một lần.
9. `user_roles` chỉ chứa `user_id`, `role_id`; `role_permissions` chỉ chứa `role_id`, `permission_id`. Hai bảng không có assignment metadata trong revision này.
10. Xóa user/role/permission không được cascade sang xóa entity đích ngoài ý muốn. Baseline dùng FK `RESTRICT`/`NO ACTION`; vòng đời deactivate là hướng ưu tiên cho user và role.
11. `UserStatus` gồm chính xác `PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`, `LOCKED`; database default là `PENDING_ACTIVATION`. Không được thêm, bỏ hoặc đổi nghĩa enum nếu chưa revise spec.
12. `FEATURE-006` chỉ persist trạng thái; không triển khai business state transitions.
13. `provider`/`provider_id`, verification fields và FCM/device fields bị hoãn khỏi baseline migration sang các feature tương ứng đã nêu trong Approved Product Decisions.

### Lifecycle

| Aggregate | Initial State | Allowed Foundation Change | Terminal/Delete Behavior |
|---|---|---|---|
| User | `PENDING_ACTIVATION` | Tập trạng thái có thể persist: `PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`, `LOCKED`; không triển khai business transition trong foundation | Ưu tiên deactivate; hard delete ngoài phạm vi |
| Role | `is_active = true` | Kích hoạt/vô hiệu hóa | Không xóa khi đang được gán |
| Permission | Không có state trong input | Metadata foundation | Không xóa khi đang được role tham chiếu |

Đây chưa phải state machine authentication. Activation, verification, reset password và lock policy chi tiết đều ngoài phạm vi.

## 4. Data Model and Schema Impact

### Naming Normalization

- Chuẩn hóa bảng input `permission` thành `permissions` và `roles_permission` thành `role_permissions` để nhất quán số nhiều và đúng ngữ nghĩa many-to-many.
- Chuẩn hóa `users.password` thành `users.password_hash` để ngăn hiểu sai rằng có thể lưu password dạng rõ.
- Chuẩn hóa `avatar` thành `avatar_url` nếu giá trị là URL/object key. Nếu Product Owner muốn lưu binary hoặc cấu trúc media khác, cần quyết định riêng trước implementation.
- Không thêm `role_id` hoặc `permission_id` vào `users`.

### Proposed Baseline Schema

| Table.Column | PostgreSQL Type | Nullability / Default | Key / Constraint | Index Rationale |
|---|---|---|---|---|
| `users.id` | `uuid` | `NOT NULL`; application tạo trước persist | PK UUID | PK index |
| `users.username` | `varchar(100)` | `NOT NULL` | length > 0; `username = btrim(username)`; unique không phân biệt hoa thường | `UNIQUE (lower(username))` |
| `users.email` | `varchar(320)` | `NOT NULL` | length > 0; `email = btrim(email)`; unique không phân biệt hoa thường | `UNIQUE (lower(email))` |
| `users.password_hash` | `varchar(255)` | `NOT NULL` | không lưu cleartext | Không lập index |
| `users.avatar_url` | `varchar(2048)` | `NULL` | Lưu URL/path | Không lập index |
| `users.created_at` | `timestamptz` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Database cấp; immutable creation instant UTC | Index riêng chưa cần trong foundation |
| `users.status` | `varchar(32)` | `NOT NULL DEFAULT 'PENDING_ACTIVATION'` | `CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'INACTIVE', 'LOCKED'))` | Index đơn chỉ thêm khi có query đã chứng minh; low cardinality có thể không hữu ích |
| `users.phone` | `varchar(32)` | `NULL` | Chưa unique | Không tạo unique/index trong baseline |
| `roles.role_id` | `uuid` | `NOT NULL`; application tạo trước persist | PK UUID | PK index |
| `roles.role_name` | `varchar(100)` | `NOT NULL` | length > 0; unique không phân biệt hoa thường | `UNIQUE (lower(role_name))` |
| `roles.description` | `varchar(500)` | `NULL` | — | Không lập index |
| `roles.is_active` | `boolean` | `NOT NULL DEFAULT true` | — | Không lập index mặc định |
| `roles.created_at` | `timestamptz` | `NOT NULL DEFAULT CURRENT_TIMESTAMP` | Database cấp; immutable creation instant UTC | Không lập index mặc định |
| `permissions.id` | `uuid` | `NOT NULL`; application tạo trước persist | PK UUID | PK index |
| `permissions.code` | `varchar(150)` | `NOT NULL` | length > 0; unique không phân biệt hoa thường | `UNIQUE (lower(code))` |
| `permissions.description` | `varchar(500)` | `NULL` | — | Không lập index |
| `user_roles.user_id` | `uuid` | `NOT NULL` | FK → `public.users(id)` with `ON DELETE RESTRICT` | Composite PK prefix hỗ trợ lookup theo user |
| `user_roles.role_id` | `uuid` | `NOT NULL` | FK → `public.roles(role_id)` with `ON DELETE RESTRICT` | Thêm index `user_roles(role_id)` cho reverse lookup |
| `role_permissions.role_id` | `uuid` | `NOT NULL` | FK → `public.roles(role_id)` with `ON DELETE RESTRICT` | Composite PK prefix hỗ trợ lookup theo role |
| `role_permissions.permission_id` | `uuid` | `NOT NULL` | FK → `public.permissions(id)` with `ON DELETE RESTRICT` | Thêm index `role_permissions(permission_id)` cho reverse lookup |

### Approved Key Constraints and Referential Integrity

- `users.id`, `roles.role_id` và `permissions.id` dùng primary key kiểu UUID.
- UUID do application tạo và gán trước `persist`; migration không dùng database UUID default hay yêu cầu extension `pgcrypto` chỉ để sinh ID.
- `user_roles` dùng composite PK `(user_id, role_id)` theo quyết định Product Owner; đồng thời ngăn duplicate assignment.
- `role_permissions` dùng composite PK `(role_id, permission_id)` theo quyết định Product Owner; đồng thời ngăn duplicate grant.
- Tất cả foreign key columns phải có index phù hợp với hướng truy vấn; PostgreSQL không tự tạo index cho phía referencing FK.
- Không dùng cross-module JPA relationship. Mọi entity ở schema này đều do module `iam` sở hữu.
- Physical foreign keys cùng module là bắt buộc trừ khi reviewer có quyết định khác kèm lý do vận hành.
- Mọi table/FK được schema-qualify dưới `public`; hai join tables do Flyway quản lý và không có JPA entity/embedded ID tương ứng.

### Deferred Fields

| Input Field | Approved Disposition | Reason and Consequence | Baseline Treatment |
|---|---|---|---|
| `provider`, `provider_id` | `DEFER` | OAuth2/provider identity ngoài phạm vi. | Không tạo trong baseline migration; feature provider/OAuth2 sau phải đặc tả lại |
| `verification_code`, `verification_expired_at`, `is_verified`, `email_sent_count`, `email_sent_date` | `DEFER` | Activation/verification ngoài phạm vi; code thô có rủi ro. | Không tạo trong baseline migration; Activation Feature sở hữu thiết kế sau |
| `fcm_token` và device model | `DEFER` | Token có lifecycle theo device, không phù hợp một cột trên `users`. | Không tạo trong baseline migration; Notification Feature sở hữu thiết kế sau |
| `phone` | `KEEP` | Hồ sơ nhân viên có thể có phone nhưng chưa đặt uniqueness. | `NULL`, không unique/index trong baseline |
| `avatar` | `KEEP` | Lưu reference, không lưu binary trong `users`. | `avatar_url varchar(2048) NULL`, semantics URL/path |

Schema cuối cùng sau khi áp dụng đầy đủ các Flyway migrations phải không chứa `user_roles.assigned_at` và `user_roles.assigned_by`. Nếu có bằng chứng V1 chưa được áp dụng tại bất kỳ môi trường nào cần bảo toàn, có thể chỉnh sửa V1 để tạo trực tiếp schema đích. Nếu V1 đã được áp dụng tại một môi trường cần bảo toàn, phải giữ nguyên V1 và sử dụng forward migration để loại bỏ hai cột. Nếu chưa xác minh được trạng thái áp dụng V1, không được chỉnh sửa V1. Các deferred provider, verification và FCM/device fields vẫn không được tạo sẵn dưới dạng nullable placeholders trong bất kỳ migration nào thuộc `FEATURE-006`.

### Migration and Rollback Strategy

- Flyway migration phải versioned, forward-only và tạo bảng theo thứ tự: `users` → `roles` → `permissions` → `user_roles` → `role_permissions`.
- DDL và mapping dùng PostgreSQL schema `public` một cách nhất quán.
- Production không dùng `ddl-auto=create`, `update` hoặc `create-drop`; đề xuất `spring.jpa.hibernate.ddl-auto=validate` và `spring.flyway.enabled=true` cho runtime có database.
- Rollback production là restore từ backup/snapshot đã kiểm chứng hoặc forward-fix migration; không tự chạy destructive down migration.
- Trước rollout phải có backup/restore rehearsal phù hợp môi trường. Nếu migration thất bại, deployment phải dừng và operator phải phân loại transactional rollback hay partial/non-transactional failure bằng Flyway schema history cùng schema catalog thực tế trước khi chọn approved recovery path; không mặc định chạy repair hoặc retry.
- Vì đây là schema mới, rollback release ưu tiên rollback application nhưng chỉ rollback schema khi xác nhận không có dữ liệu cần giữ. Không drop bảng tự động.
- Trước khi sửa migration, implementation plan version mới phải inventory `flyway_schema_history` và catalog trên mọi environment cần bảo toàn. Nếu `V1__create_iam_foundation.sql` chưa từng được apply ở bất kỳ environment cần bảo toàn nào, plan có thể đăng ký cập nhật V1 để bỏ `assigned_at`/`assigned_by`. Nếu V1 đã apply ở ít nhất một environment cần bảo toàn, tuyệt đối không sửa checksum/nội dung migration đã chạy; phải tạo forward migration version tiếp theo để drop hai columns/constraint/index liên quan theo procedure đã review.
- Bất kể chọn cập nhật V1 hay thêm forward migration, schema đích sau khi áp dụng toàn bộ Flyway migrations phải có `public.user_roles` gồm chính xác `user_id UUID NOT NULL`, `role_id UUID NOT NULL`, composite primary key `(user_id, role_id)` và hai foreign keys tới `public.users(id)`/`public.roles(role_id)`. `assigned_at`, `assigned_by` cùng FK/index liên quan không được tồn tại trong schema cuối cùng.
- Revision này không xác nhận V1 đã hoặc chưa được apply. Không reset database, xóa schema, sửa Flyway history hoặc mặc định chạy `repair` để đạt kiến trúc mới.

## 5. API and Contract Specifications

### 5.1 External HTTP Endpoints

Không có endpoint HTTP trong `FEATURE-006`. Controller, login, account administration và RBAC enforcement thuộc feature sau.

### 5.2 Inter-Module Synchronous Java Interface

Không phát hành Java service interface mới trong `FEATURE-006`. Các module khác bị cấm import entity/repository trong `iam.internal`. DTO trong `api.dto` chỉ được xem là contract khi Spec Review chốt consumer và method cụ thể; không suy diễn contract từ skeleton hiện tại.

### 5.3 Domain Events

Không phát hành domain event trong `FEATURE-006`.

## 6. Entity, Enum, Repository, DTO, and Mapper Design

### Entity Design

| Type | Package | Minimal Mapping |
|---|---|---|
| `UserEntity` | `iam.internal.entity` | `users`; UUID id; username, email, passwordHash, avatarUrl, createdAt, status, phone; sở hữu `Set<RoleEntity> roles` qua `public.user_roles`; không chứa role/permission ID trực tiếp |
| `RoleEntity` | `iam.internal.entity` | `roles`; UUID roleId; roleName, description, active, createdAt; sở hữu `Set<PermissionEntity> permissions` qua `public.role_permissions` |
| `PermissionEntity` | `iam.internal.entity` | `permissions`; UUID id; code, description |

Package `iam.internal.entity` chỉ chứa ba JPA entity trên. Không tạo `UserRoleEntity`, `UserRoleId`, `RolePermissionEntity` hoặc `RolePermissionId`. Hai join tables vẫn tồn tại vật lý và do Flyway quản lý.

Cascade/fetch policy bắt buộc cho từng association:

| Association | Mapping | Fetch | Cascade | Inverse Side |
|---|---|---|---|---|
| `UserEntity.roles` → `RoleEntity` | `@ManyToMany` + `@JoinTable(name="user_roles", schema="public", joinColumns=@JoinColumn(name="user_id"), inverseJoinColumns=@JoinColumn(name="role_id"))` | Explicit `LAZY` | Không khai báo cascade; đặc biệt cấm `ALL`, `REMOVE`, `PERSIST` | Không khai báo collection ngược trong `RoleEntity` khi chưa có use case |
| `RoleEntity.permissions` → `PermissionEntity` | `@ManyToMany` + `@JoinTable(name="role_permissions", schema="public", joinColumns=@JoinColumn(name="role_id"), inverseJoinColumns=@JoinColumn(name="permission_id"))` | Explicit `LAZY` | Không khai báo cascade; đặc biệt cấm `ALL`, `REMOVE`, `PERSIST` | Không khai báo collection ngược trong `PermissionEntity` khi chưa có use case |

Foundation có đúng hai JPA associations trên. Không association nào dùng cascade `ALL`, `REMOVE` hoặc `PERSIST`; mặc định không khai báo cascade. Join-row insert/delete được điều khiển bằng thay đổi owning collection trong transaction, không cascade lifecycle sang `UserEntity`, `RoleEntity` hoặc `PermissionEntity` đích.

JPA consistency requirements:

- `UserEntity.id`, `RoleEntity.roleId` và `PermissionEntity.id` là `UUID`, được application gán trước persist; không dùng database-generated UUID mapping.
- Các field `createdAt` dùng `Instant`, map tới `TIMESTAMPTZ`, `insertable = false`, `updatable = false` vì database cấp `DEFAULT CURRENT_TIMESTAMP`. Test cần refresh/reload entity trước khi assert generated timestamp.
- `UserStatus` dùng `EnumType.STRING`; Java enum và database `CHECK` phải có đúng cùng bốn giá trị.
- Table mapping phải chỉ rõ schema `public` hoặc tuân theo một mapping strategy được integration test chứng minh là resolve chính xác về `public`.
- Hai collection được khởi tạo bằng `new HashSet<>()`; equality/hashCode của entity không được dựa trên mutable collections.

### Enum Design

- `UserStatus` bắt buộc nằm trong `com.aquasuite.ims.module.iam.internal.enums`, được `UserEntity` import và persistence bằng `@Enumerated(EnumType.STRING)`.
- Tập giá trị đã được Product Owner chốt và phải chính xác: `PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`, `LOCKED`.
- Database default đã được chốt là `PENDING_ACTIVATION`. Entity có thể khởi tạo cùng giá trị để hành vi in-memory nhất quán, nhưng database constraint/default vẫn là nguồn bảo vệ persistence.
- Không tạo `Provider` enum trong baseline khi OAuth2/provider bị hoãn.

### Repository Design

| Repository | Minimal Responsibility |
|---|---|
| `UserRepository extends JpaRepository<UserEntity, UUID>` | Persistence lookup tối thiểu theo username/email không phân biệt hoa thường và existence checks; không thực hiện credential verification hoặc trả password qua projection công khai |
| `RoleRepository extends JpaRepository<RoleEntity, UUID>` | lookup role name không phân biệt hoa thường và active state khi có use case |
| `PermissionRepository extends JpaRepository<PermissionEntity, UUID>` | lookup theo code không phân biệt hoa thường |

Chỉ có ba repository cho ba entity chính. Không tạo repository cho join tables khi không có join entity. Chỉ khai báo query method thực sự được integration test sử dụng; tránh tạo repository API speculative. Service transaction boundary thuộc feature/use case sau.

### DTO and Mapper Design

| Type | Package | Fields / Rules |
|---|---|---|
| Existing `UserRequest` | `iam.api.dto` | Giữ nguyên public contract hiện tại; không thêm create/login semantics trong foundation |
| Existing `UserResponse` | `iam.api.dto` | Giữ nguyên public contract hiện tại; không thêm field hay expose persistence model trong foundation |
| Existing `UserExternalService` | `iam.api` | Giữ nguyên; không thêm authentication/authorization operation trong foundation |
| Verification fixture/projection, nếu cần | Test source only | Chỉ chứa field cần cho persistence verification; không trở thành production/public contract và không chứa password hash/token trong output |

`FEATURE-006` không thay đổi public DTO/service contract và không tạo production DTO/mapper vì không có consumer hợp lệ trong scope. CP4 dùng production inventory/static inspection cùng test-only fixture nếu cần để chứng minh sensitive fields không bị expose. Không thêm mapping dependency chỉ vì foundation.

`FEATURE-006` không implement, annotate hoặc chỉnh sửa `UserController`, `UserService` hay `UserServiceImpl`; controller/service use cases thuộc feature sau. Implementation plan version mới phải ghi và đối chiếu SHA-256 byte-for-byte cho sáu protected skeleton files: `UserExternalService.java`, `UserRequest.java`, `UserResponse.java`, `UserController.java`, `UserService.java`, `UserServiceImpl.java`.

## 7. PostgreSQL and Flyway Configuration by Profile

### Common Configuration

- Flyway dependencies đã xuất hiện trong implementation work của Plan v2; revision này không sửa `pom.xml`. Plan v3 phải kiểm tra và giữ dependency set tối thiểu phù hợp architecture mới.
- Common config chỉ chứa non-secret behavior, ví dụ `spring.jpa.hibernate.ddl-auto=validate`, timezone/SQL settings đã được review, và location migration mặc định.
- Không đặt `spring.profiles.active` trong repository.

### Development and Production Profiles

- `application-dev.yml` và `application-prod.yml` tham chiếu externalized properties: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`, `SPRING_DATASOURCE_PASSWORD`.
- Không cung cấp password mặc định hoặc fallback rỗng cho production.
- Flyway dùng cùng datasource hoặc credential migration riêng do deployment environment/secret manager cung cấp; lựa chọn này là quyết định vận hành trước implementation.

### Test Profile

- Integration tests phải chạy PostgreSQL thật, ưu tiên Testcontainers PostgreSQL với database cô lập cho mỗi test suite.
- Trước implementation, plan owner phải chạy prerequisite check xác nhận local Docker/container runtime và CI runner hỗ trợ Testcontainers, image pull và thời gian khởi động phù hợp.
- `application-test.yml` không hardcode shared database credential và không trỏ production database.
- Test startup phải để Flyway tạo schema từ migration rồi để Hibernate validate mappings.
- H2 không được dùng làm bằng chứng duy nhất vì khác PostgreSQL về type, constraint và index behavior.

Nếu môi trường không hỗ trợ Testcontainers:

1. Dừng CP5 và ghi rõ prerequisite `NOT_AVAILABLE`; không âm thầm skip PostgreSQL integration tests.
2. Dùng PostgreSQL service/container do CI quản lý, database/schema cô lập theo test run và credential được inject qua secret management/CI variables.
3. Chạy cùng Flyway migrations, Hibernate validation và toàn bộ catalog/constraint/repository assertions như Testcontainers path.
4. Nếu cả Testcontainers và PostgreSQL service cô lập đều không khả dụng, implementation chưa đủ verification evidence để qua Code Review; unit test/H2 không thay thế điều kiện này.

## 8. Concurrency, Transactions, and Failure Scenarios

### Concurrency Controls

- Database unique indexes là nguồn bảo vệ cuối cùng cho username, email, role name, permission code và composite assignments.
- Application-level existence check chỉ hỗ trợ thông báo thân thiện, không thay thế unique constraint.
- Concurrent duplicate inserts phải được translate thành domain error trong feature service tương lai; error code cụ thể chưa thuộc foundation.
- Không cần optimistic lock trong baseline vì chưa có update use case được duyệt. Nếu profile/account administration sau có concurrent updates, feature đó phải đánh giá `@Version`.

### Transaction Boundaries

- Mỗi thao tác thay đổi aggregate và join rows liên quan phải nằm trong một transaction ở service layer tương lai.
- Repository không tự định nghĩa business transaction.
- Migration chạy dưới lifecycle/transaction semantics mà Flyway và PostgreSQL hỗ trợ; failure phải chặn startup/deployment.

### Failure and Recovery

- Constraint violation không được retry mù; transaction rollback và trả lỗi được feature service tương lai định nghĩa.
- Datasource/Flyway failure phải fail fast, không fallback sang in-memory database.
- Schema validation mismatch phải fail startup để ngăn chạy application trên schema không tương thích.

## 9. Implementation Checkpoints

Các checkpoint dưới đây là cổng xác minh đề xuất cho implementation plan tương lai, không phải authorization triển khai:

| Checkpoint | Scope | Exit Evidence |
|---|---|---|
| `CP1 — PostgreSQL + Flyway` | Dependency, externalized profile config, migration-state inventory và five-table schema | Xác định V1 đã apply ở environment cần bảo toàn hay chưa; chọn update V1 hoặc forward migration hợp lệ; Flyway chạy trên PostgreSQL sạch; không hardcode credential |
| `CP2 — Entity + Relationships` | Ba entities, `UserStatus` trong `internal.enums`, hai unidirectional many-to-many mappings | Context JPA load và Hibernate validate thành công; metadata/relationship tests xác nhận owning side, join table, `LAZY` và no-cascade rules |
| `CP3 — Repository` | Đúng ba Spring Data repositories và query methods tối thiểu | Repository integration tests xác nhận lookup, uniqueness và traversal qua owning collections |
| `CP4 — DTO / Mapper Scope Guard` | Không tạo production DTO/mapper nếu không có consumer; bảo toàn sáu protected skeletons | Production inventory và SHA-256 fingerprint xác nhận không có DTO/mapper speculative và sáu files không đổi; sensitive-field test không lộ `password_hash` |
| `CP5 — Persistence & Integration Tests` | End-to-end migration/persistence on PostgreSQL | Testcontainers hoặc isolated CI PostgreSQL chạy Flyway từ empty DB; CRUD/join/constraint/generated timestamp tests pass và rollback transaction isolation được chứng minh |

Mỗi checkpoint chỉ được bắt đầu sau approved Feature Spec, approved implementation plan và đúng file ownership của plan.

## 10. Acceptance Criteria

- [ ] **AC-01**: Repository có đúng một canonical spec mang `SPEC_ID: FEATURE-006`. Active Plan v2, approval và implementation handoff dựa trên Spec v0.3.2 được ghi nhận là stale đối với revision v0.4.0; không tiếp tục implementation trước Spec Review mới và approved Plan v3. Existing implementation work được bảo toàn, không quy thành thay đổi do Spec revision tạo.
- [ ] **AC-02**: Schema `public` sử dụng PostgreSQL và Flyway, gồm `users`, `roles`, `permissions`, `user_roles`, `role_permissions`; ba bảng chính dùng UUID PK do application cấp và các PK, FK, unique, nullability, index được định nghĩa rõ.
- [ ] **AC-03**: `role_permissions` là tên chuẩn; `users` không chứa `role_id` hoặc `permission_id`; `user_roles` chỉ có `user_id`, `role_id` với composite PK `(user_id, role_id)`; `role_permissions` chỉ có `role_id`, `permission_id` với composite PK `(role_id, permission_id)`. Không có `assigned_at` hoặc `assigned_by`.
- [ ] **AC-04**: Database áp dụng case-insensitive uniqueness độc lập cho từng business key `users.username`, `users.email`, `roles.role_name` và `permissions.code`; với mỗi key, insert giá trị chỉ khác hoa/thường phải bị reject, kể cả khi hai insert cạnh tranh. Whitespace validation là invariant riêng chỉ được yêu cầu trong revision này cho `username` và `email`: leading/trailing whitespace bị `CHECK (value = btrim(value))` reject. Không diễn giải unique index `lower(...)` là trim/normalization, không yêu cầu foundation tự sửa input, và không suy rộng whitespace constraint sang `role_name` hoặc `permission.code`.
- [ ] **AC-05**: Password chỉ được lưu ở `password_hash`; không có production DTO/mapper/serialization surface mới trong scope và không có password/hash/token/verification code trong output hoặc log. Verification fixture nếu cần chỉ nằm trong test source.
- [ ] **AC-06**: Entity và relationship nằm trong `com.aquasuite.ims.module.iam.internal`; module khác không import `internal` và không có cross-module JPA relationship.
- [ ] **AC-07**: Chỉ có ba JPA entities và đúng hai unidirectional `@ManyToMany(fetch = LAZY)` associations: `UserEntity.roles` sở hữu `user_roles`, `RoleEntity.permissions` sở hữu `role_permissions`. Không dùng cascade `ALL`, `REMOVE` hoặc `PERSIST`; không có inverse collection, join entity hoặc embedded ID class.
- [ ] **AC-08**: Datasource config dùng externalized environment/secret management; không có credential thật hoặc default production secret trong repository.
- [ ] **AC-09**: Profile `test` chạy migration và persistence tests trên PostgreSQL cô lập qua Testcontainers ưu tiên hoặc isolated CI PostgreSQL fallback; Flyway migration từ empty database và Hibernate schema validation đều pass. Thiếu cả hai môi trường là verification failure, không được thay bằng H2.
- [ ] **AC-10**: Database `CHECK` chỉ chấp nhận `PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`, `LOCKED`; insert SQL không truyền `status` nhận database default `PENDING_ACTIVATION`; feature không triển khai business state transitions.
- [ ] **AC-11**: Không triển khai login, JWT, refresh token, Redis, Spring Security config, OAuth2, customer registration, RBAC enforcement, controller hoặc email processing.
- [ ] **AC-12**: Sáu protected skeleton files (`UserRequest`, `UserResponse`, `UserExternalService`, `UserController`, `UserService`, `UserServiceImpl`) được giữ nguyên; controller/service không được implement trong foundation. Approved Plan v3 phải lưu baseline và hậu kiểm SHA-256 byte-for-byte, không dựa riêng vào Git diff cho untracked files.
- [ ] **AC-13**: Recovery phân biệt migration transactional đã rollback với partial/non-transactional failure. Trước mọi recovery phải kiểm tra Flyway schema history và schema thực tế; không mặc định chạy `flyway repair`. Chỉ repair khi metadata thật sự cần sửa, theo approved recovery procedure; không tự drop schema/data.
- [ ] **AC-14**: `provider`/`provider_id`, verification fields, `fcm_token` và mọi FCM/device table đều không tồn tại trong baseline migration; chúng lần lượt được hoãn sang provider/OAuth2, Activation và Notification features, không có phương án tùy chọn giữ lại trong `FEATURE-006`.
- [ ] **AC-15**: `phone` nullable và không unique; `avatar_url` nullable, lưu URL/path; không có binary avatar trong `users`.
- [ ] **AC-16**: `users.created_at` và `roles.created_at` là `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP`; entity dùng `Instant`, database cấp giá trị và integration test reload để xác minh instant UTC.
- [ ] **AC-17**: JPA `UserStatus` mapping dùng `EnumType.STRING` và round-trip riêng từng giá trị trong bốn enum values; test này độc lập với test database default tại `AC-10`.

## 11. AC-to-Test Verification Matrix

| AC ID | Verification Method | Test Scope / Target | Expected Result | Pass/Fail Criteria |
|---|---|---|---|---|
| `AC-01` | Current-state inventory + spec/plan/review/handoff fingerprint comparison | Canonical Spec, active Plan v2, approvals, implementation work | Một canonical Spec v0.4.0; prior Spec/Plan approvals được giữ làm history nhưng stale; implementation dừng chờ re-plan | PASS khi reviewer tái kiểm chứng version/hash state và không dùng approval v0.3.2/Plan v2 để tiếp tục implementation |
| `AC-02` | Flyway integration test + schema introspection | Empty PostgreSQL database, schema `public` | Đủ tables, columns, constraints và indexes; PK ba bảng chính là UUID, không có DB UUID default | PASS khi catalog assertions khớp schema và application-assigned UUID persist thành công |
| `AC-03` | Schema assertions | Join tables và `users` | Mỗi join table chỉ có hai FK columns, composite PK đúng thứ tự; không có assignment metadata hoặc role/permission FK trong users | PASS khi duplicate pair bị reject, exact column inventory khớp và `assigned_at`/`assigned_by` vắng mặt |
| `AC-04` | Hai nhóm PostgreSQL integration tests độc lập | (A) từng key: username, email, role name, permission code; (B) chỉ username/email whitespace checks | (A) mỗi case-variant duplicate, gồm concurrent case, bị unique index reject; (B) leading/trailing whitespace bị check reject nhưng internal whitespace không bị foundation sửa | PASS khi đủ bốn uniqueness cases và hai whitespace cases pass riêng biệt; không dùng kết quả `lower(...)` để tuyên bố whitespace normalization và không yêu cầu whitespace rejection cho role name/permission code |
| `AC-05` | Production surface inventory + static/log search + optional test-only fixture | Entity and test source | Chỉ `password_hash` persisted; không có production DTO/mapper mới; sensitive fields không leak | PASS khi inventory không có production exposure surface và fixture/log assertions không chứa sensitive data |
| `AC-06` | Package/dependency inspection | IAM and other modules | Không cross-import `internal`; không cross-module entity relation | PASS khi static checks/search không phát hiện vi phạm |
| `AC-07` | Static annotation/mapping inspection + transactional persistence tests | Ba entity classes, enum package và hai owning many-to-many collections | Đúng hai unidirectional associations, `LAZY`, exact join-table columns, không `ALL`/`REMOVE`/`PERSIST`, không join entity/embedded ID | PASS khi lưu/đọc User với nhiều Role và Role với nhiều Permission; lazy access trong transaction hoạt động; duplicate join pair bị PK reject; xóa/collection update không cascade xóa entity đích |
| `AC-08` | Config test + secret-pattern review | YAML and environment binding | Values được externalize, prod thiếu secret thì fail | PASS khi không có hardcoded credential và binding hoạt động |
| `AC-09` | Environment prerequisite check + PostgreSQL integration suite | Testcontainers hoặc isolated CI PostgreSQL + Flyway + JPA | Migration sạch, validate và repository tests pass | PASS khi một PostgreSQL path được chứng minh; FAIL/BLOCKED khi chỉ có H2/unit tests |
| `AC-10` | Native SQL/schema-default integration test | `users.status` database constraint/default | Check constraint có đúng bốn literals; omitted column nhận `PENDING_ACTIVATION`; không có transition service | PASS khi default và invalid-value rejection được chứng minh độc lập với JPA enum mapping |
| `AC-11` | Git diff scope review | Source/dependencies/config | Không có out-of-scope implementation | PASS khi diff chỉ chứa approved persistence scope |
| `AC-12` | SHA-256 baseline/current comparison + scope inventory | Sáu protected skeleton files | Sáu files không đổi; controller/service chưa được implement | PASS khi Plan v3 lưu baseline hashes, handoff hashes khớp byte-for-byte và verification không phụ thuộc riêng vào Git diff |
| `AC-13` | Hai failure-mode rehearsals + schema-history/catalog inspection | Transactional DDL failure và simulated partial/non-transactional state | Transactional case rollback sạch; partial case dừng để phân loại history/schema trước approved recovery; không repair mặc định | PASS khi evidence ghi Flyway history + actual schema, recovery path được duyệt, và repair chỉ xuất hiện khi chứng minh metadata cần sửa |
| `AC-14` | Migration column inventory | `public.users` và schema catalog | Không có deferred provider/verification/FCM fields | PASS khi catalog/search xác nhận chúng vắng mặt |
| `AC-15` | Schema + persistence test | `phone`, `avatar_url` | Null được chấp nhận; phone không unique; avatar là URL/path string | PASS khi null/duplicate phone và URL/path cases đúng constraint |
| `AC-16` | Schema catalog + persistence reload test | `created_at` columns and entity mapping | DB tạo non-null `TIMESTAMPTZ`; JPA đọc thành `Instant` | PASS khi insert không truyền created_at, reload nhận instant hợp lệ và update không đổi created_at |
| `AC-17` | JPA persistence round-trip test | `UserStatus` enum mapping | Mỗi enum value được lưu/đọc bằng tên string chính xác | PASS khi cả bốn round-trip cases pass; test không dựa vào omitted-column database default |

## 12. Rollback and Operational Verification

### Pre-Deployment Verification

1. Chạy toàn bộ migrations trên PostgreSQL trống cùng version mục tiêu.
2. Chạy integration tests và schema catalog assertions.
3. Chạy migration rehearsal trên bản sao dữ liệu/môi trường staging nếu deployment không còn là greenfield.
4. Xác minh backup có thể restore và không chứa credential trong artifact/log.
5. Xác minh application khởi động với `ddl-auto=validate` và fail khi schema bị cố ý làm lệch trong negative test.

### Rollback Criteria

- Dừng deployment khi Flyway, schema validation hoặc constraint tests thất bại.
- Với migration chỉ chứa transactional PostgreSQL DDL và thất bại trước commit, xác minh transaction đã rollback, Flyway schema history không ghi success sai và schema thực tế không còn object partial trước khi retry migration nguyên bản.
- Với partial/non-transactional failure (ví dụ statement ngoài transaction, operator interruption hoặc metadata/schema không đồng bộ), không giả định rollback sạch và không retry ngay. Trước recovery phải chụp/đối chiếu: Flyway schema history row/checksum/success state, catalog của tables/columns/constraints/indexes thực tế, application logs và backup availability.
- `flyway repair` không phải bước mặc định. Chỉ chạy repair khi inspection chứng minh Flyway metadata cần sửa, exact repair action nằm trong approved recovery procedure, con người phê duyệt, và schema thực tế đã được reconcile an toàn với migration intent. Repair không thay thế schema/data recovery.
- Khi schema thực tế bị partial nhưng metadata không cần repair, dùng approved forward-fix hoặc restore path phù hợp; không sửa metadata chỉ để ép migration chạy tiếp.
- Nếu migration đã thành công nhưng application không tương thích, rollback application trước; giữ schema additive nếu an toàn.
- Nếu bắt buộc đảo schema, restore backup hoặc áp dụng forward-fix migration đã được con người duyệt. Không tự động drop bảng/cột.

## 13. Regression Risks

- Tên `permission`/`roles_permission` từ input không nhất quán với convention; code hoặc SQL viết sớm có thể phụ thuộc tên cũ.
- Case-insensitive uniqueness không tự trim whitespace; thiếu test tách biệt có thể cho phép key có leading/trailing whitespace hoặc tạo kỳ vọng normalization sai.
- Eager/bidirectional relationships hoặc cascade remove có thể gây query explosion hay xóa assignment ngoài ý muốn.
- Vi phạm quyết định defer bằng cách đưa provider/verification/FCM vào baseline sẽ khóa schema vào workflow ngoài phạm vi.
- Dùng H2 làm test duy nhất có thể bỏ sót behavior PostgreSQL.
- Docker hoặc CI runner có thể không hỗ trợ Testcontainers; implementation phải kiểm tra sớm và chuẩn bị isolated PostgreSQL service thay thế.
- `CURRENT_TIMESTAMP` phản ánh transaction timestamp và cách hiển thị phụ thuộc session timezone; tests phải so sánh theo instant/UTC thay vì chuỗi timezone cục bộ.
- `spring.jpa.hibernate.ddl-auto=update` có thể làm schema drift khỏi Flyway.
- IAM skeleton hiện là untracked user work; implementation phải bảo toàn và review diff cẩn thận, không overwrite ngoài plan.

## 14. Remaining Blockers

Không còn Product Owner architecture decision nào mở trong phạm vi `FEATURE-006`. Một operational decision phải được Plan v3 giải quyết bằng evidence, không được đoán trong Spec:

1. `V1_APPLICATION_STATE`: Chưa có bằng chứng toàn cục rằng V1 chưa từng apply ở mọi environment cần bảo toàn. Plan v3 phải inventory Flyway history/catalog: chỉ cập nhật V1 khi chứng minh chưa apply ở đâu cần bảo toàn; nếu đã apply, dùng forward migration tiếp theo.

Các điều kiện sau vẫn chặn implementation nhưng không chặn việc gửi Spec Review:

2. `SPEC_REVIEW_REQUIRED`: Revision `0.4.0` chưa có independent Spec Review; approval v0.3.2 không áp dụng.
3. `IMPLEMENTATION_PLAN_REQUIRED`: Active Plan v2 đã stale; phải tạo Plan v3 sau khi Spec v0.4.0 được duyệt.
4. `TEST_ENVIRONMENT_PREREQUISITE`: Trước implementation phải xác minh Docker/Testcontainers hoặc isolated PostgreSQL service; H2 không thay thế.

## 15. Review Checklist

- [ ] Metadata là `FEATURE-006`, version `0.4.0`, status `DRAFT_FOR_REVIEW`; approval v0.3.2 được giữ nguyên nhưng không áp dụng.
- [ ] Scope chỉ gồm IAM persistence foundation cho nhân viên nội bộ.
- [ ] Authentication/authorization, activation, token, RBAC enforcement, controller và public operation mới nằm ngoài phạm vi.
- [ ] Schema `public` có đúng năm bảng baseline và không có deferred fields.
- [ ] UUID PK, application-side UUID generation, composite PK và FK types nhất quán.
- [ ] `user_roles` và `role_permissions` mỗi bảng chỉ có hai FK columns cùng composite PK; không có `assigned_at`/`assigned_by`.
- [ ] Package `internal.entity` chỉ có ba JPA entities; không có join entity/embedded ID; `UserStatus` nằm trong `internal.enums`.
- [ ] Có đúng hai unidirectional `ManyToMany` owning associations, explicit `LAZY`, không cascade `ALL`, `REMOVE` hoặc `PERSIST`; không khai báo inverse collections speculative.
- [ ] `UserStatus` enum, database check và default `PENDING_ACTIVATION` nhất quán; không có transition logic.
- [ ] `created_at` dùng database-generated `TIMESTAMPTZ`; JPA mapping dùng `Instant` và không ghi đè.
- [ ] Phone/avatar constraints khớp quyết định đã duyệt.
- [ ] Sáu protected IAM skeleton files được giữ nguyên; Plan v3 phải lưu và hậu kiểm SHA-256 cho cả sáu.
- [ ] Testcontainers prerequisite và isolated PostgreSQL fallback được mô tả; H2 không thay thế PostgreSQL verification.
- [ ] Không có credential, secret hoặc dữ liệu nhạy cảm thật trong spec.
- [ ] AC-to-Test matrix bao phủ schema, cascade/fetch mapping, deferred fields, generated timestamps, enum mapping và scope boundary.
- [ ] Rollback/recovery phân loại transactional với partial/non-transactional failure, kiểm tra Flyway history + schema thực tế và không repair mặc định.
- [ ] Migration strategy rẽ nhánh theo evidence V1 đã apply: update V1 chỉ khi an toàn; nếu đã apply thì forward migration; không reset database/Flyway history.
- [ ] Reviewer không diễn giải draft này thành `SPEC_REVIEW: APPROVED` nếu chưa có approval evidence độc lập.

## 16. Review and Next Action

- Draft này không phải implementation plan và không cấp quyền sửa source/config/migration.
- Product Owner/Lead Architect cần kiểm tra Approved Product Decisions, Acceptance Criteria, Known Risks và Review Checklist, sau đó ghi Spec Review evidence độc lập nếu chấp thuận.
- Sau khi revision `0.4.0` được Spec Review phê duyệt ở cấp project, lập Plan version `3`; plan phải đăng ký exact files cần refactor/create/delete, migration path theo V1 application-state evidence, checkpoint verification và bảo toàn IAM skeleton/uncommitted work.
- CODEX_PLAN không tự phát hành `SPEC_REVIEW: APPROVED`.
