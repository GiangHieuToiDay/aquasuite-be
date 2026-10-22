# Use Case Specification — Manage Roles and Permissions

## Metadata

- **UC ID**: `UC-IAM-ROLES-PERMISSIONS`
- **Name**: Manage Roles and Permissions
- **Owning Module**: `iam`
- **Repository Scope**: `BE (aquasuite-be)`
- **Target Package**: `com.aquasuite.ims.module.iam`
- **Database Baseline**: PostgreSQL
- **Risk Level**: `HIGH`
- **Spec Review Status**: `READY_FOR_SPEC_REVIEW`
- **Spec Review Approver**: Product Owner / Lead Architect
- **Implementation Status**: `NOT_STARTED`

## 1. Purpose

UC cung cấp khả năng để chủ cửa hàng xem cấu hình phân quyền, quản lý danh mục permission và gán hoặc gỡ từng permission cho các role cố định của AquaSuite. Mục tiêu là duy trì một mô hình RBAC đơn giản, kiểm soát được và phản ánh thay đổi quyền ngay từ request được bảo vệ kế tiếp.

## 2. Scope

### In Scope

- Xem sáu role cố định và các permission đang gán cho từng role.
- Xem danh sách và chi tiết permission.
- Tạo permission mới.
- Chỉnh sửa `description` của permission.
- Xóa permission chưa được gán cho bất kỳ role nào.
- Gán hoặc gỡ từng permission cho role với hành vi idempotent.
- Bảo vệ toàn bộ API của UC bằng role `SHOP_OWNER`.

### Out of Scope

- Tạo, xóa, rename, kích hoạt/vô hiệu hóa hoặc sửa metadata role.
- Gán hoặc gỡ role cho user; nội dung này thuộc UC **Manage User Accounts and Status**.
- Thay đổi login, refresh token, logout hoặc vòng đời Redis session.
- Nhúng role hoặc permission vào JWT.
- Thêm Redis authority cache.
- Xây dựng permission hierarchy, explicit deny, tenant scope hoặc permission expiration.
- Thêm tên hiển thị riêng cho permission; frontend hiển thị canonical permission code bên cạnh `description` và nằm ngoài scope backend này.
- Optimistic locking/version cho permission; chỉ xem xét lại khi hệ thống hỗ trợ nhiều owner/admin cùng quản trị.
- Xây dựng durable audit trail của module `audit`.
- Thay đổi hoặc hoàn tất FEATURE-009.

## 3. Actor and Authorization

### Primary Actor

`SHOP_OWNER` là actor duy nhất được xem và quản lý Roles & Permissions.

### Authorization Rule

Mọi endpoint của UC phải được bảo vệ tại method boundary bằng:

```java
@PreAuthorize("hasRole('SHOP_OWNER')")
```

Quyền quản trị RBAC không phụ thuộc vào một permission động vì permission đó có thể bị chỉnh sửa hoặc gỡ trong chính UC này.

### Authorization Matrix

| Operation | `SHOP_OWNER` | Other Authenticated Roles | Anonymous |
|---|---:|---:|---:|
| List/get roles | Allow | Deny | Deny |
| List/get permissions | Allow | Deny | Deny |
| Create permission | Allow | Deny | Deny |
| Update permission metadata | Allow | Deny | Deny |
| Delete unused permission | Allow | Deny | Deny |
| Assign permission to role | Allow | Deny | Deny |
| Unassign permission from role | Allow | Deny | Deny |
| Create/delete/update role | No API | No API | No API |
| Assign/unassign role to user | Out of scope | Out of scope | Out of scope |

## 4. Preconditions and Postconditions

### Preconditions

- Access token hợp lệ.
- Redis session tương ứng còn active và chưa bị revoke.
- User hiện tại tồn tại trong PostgreSQL và có trạng thái `ACTIVE`.
- Authority hiện tại được resolve từ PostgreSQL và chứa `ROLE_SHOP_OWNER`.
- Database schema của UC đã được migrate thành công.
- Sáu canonical role không vi phạm invariant dữ liệu.

### Success Postconditions

- Mutation được commit toàn bộ trong một transaction hoặc không thay đổi dữ liệu.
- Permission code không bị thay đổi sau khi tạo.
- Role và `user_roles` không bị thay đổi.
- `role_permissions` không có duplicate hoặc dangling reference.
- Thay đổi assignment có hiệu lực từ protected request kế tiếp sau commit.
- JWT và Redis session hiện hữu không bị thay thế hoặc revoke chỉ vì permission thay đổi.

### Failure Postconditions

- Không có partial update.
- Không tự sửa, xóa hoặc hợp thức hóa dữ liệu role không canonical.
- Không xóa permission hoặc mapping khi validation, authorization hay transaction thất bại.

## 5. Main Flows

### MF-01 — List Roles

1. SHOP_OWNER yêu cầu danh sách role.
2. Hệ thống xác thực token, session, user status và `ROLE_SHOP_OWNER`.
3. Hệ thống đọc sáu canonical role cùng permissions đang gán.
4. Hệ thống kiểm tra mỗi role thuộc canonical set.
5. Hệ thống trả role theo thứ tự: `SHOP_OWNER`, `SALES_STAFF`, `ACCOUNTANT`, `TECHNICAL_STAFF`, `SHIPPER`, `CUSTOMER`.

### MF-02 — Get Role Detail

1. SHOP_OWNER gửi role ID.
2. Hệ thống đọc role cùng permissions.
3. Hệ thống xác nhận role code là canonical.
4. Hệ thống trả role detail ở chế độ read-only.

### MF-03 — List Permissions

1. SHOP_OWNER gửi pagination, search và sort parameters.
2. Hệ thống validate page size và sort allowlist.
3. Hệ thống search không phân biệt hoa/thường trên `code` và `description`.
4. Hệ thống trả page metadata và permission summaries.

### MF-04 — Create Permission

1. SHOP_OWNER gửi `code` và optional `description`.
2. Backend trim code và chuyển sang uppercase bằng `Locale.ROOT`.
3. Backend validate format và reserved namespace.
4. Backend kiểm tra uniqueness không phân biệt hoa/thường.
5. Hệ thống tạo permission trong một transaction.
6. Hệ thống trả `201 Created` với code uppercase.

### MF-05 — Update Permission Metadata

1. SHOP_OWNER gửi `description`.
2. Hệ thống từ chối payload chứa `code` hoặc field không được hỗ trợ.
3. Hệ thống đọc permission theo ID.
4. Hệ thống chỉ cập nhật `description` theo last-write-wins trong transaction.
5. Hệ thống trả permission sau update; code giữ nguyên.

### MF-06 — Delete Permission

1. SHOP_OWNER gửi permission ID.
2. Hệ thống xác nhận permission tồn tại.
3. Hệ thống xác nhận permission chưa được role nào sử dụng.
4. Hệ thống xóa và flush trong cùng transaction.
5. Hệ thống trả `200 OK` với `ApiResponse<Void>`.

### MF-07 — Assign Permission to Role

1. SHOP_OWNER gửi role ID và permission ID.
2. Hệ thống khóa role target cho mutation.
3. Hệ thống xác nhận role canonical và permission tồn tại.
4. Nếu mapping chưa tồn tại, hệ thống tạo mapping.
5. Nếu mapping đã tồn tại, hệ thống không tạo duplicate.
6. Hệ thống commit và trả role detail mới nhất.

### MF-08 — Unassign Permission from Role

1. SHOP_OWNER gửi role ID và permission ID.
2. Hệ thống khóa role target cho mutation.
3. Hệ thống xác nhận role canonical và permission tồn tại.
4. Nếu mapping tồn tại, hệ thống xóa mapping.
5. Nếu mapping không tồn tại, hệ thống không thay đổi dữ liệu.
6. Hệ thống commit và trả role detail mới nhất.

## 6. Alternative and Exception Flows

| ID | Condition | System Behavior |
|---|---|---|
| AF-01 | Assign mapping đã tồn tại | Trả success idempotently, không tạo duplicate |
| AF-02 | Unassign mapping không tồn tại | Trả success idempotently |
| AF-03 | Search rỗng sau trim | Xử lý như không có search filter |
| EF-01 | Token thiếu hoặc không hợp lệ | Trả `401 AUTH_001` |
| EF-02 | Actor không phải SHOP_OWNER | Trả `403 AUTH_002` |
| EF-03 | Role không tồn tại | Trả `404 IAM_003` |
| EF-04 | Role được truy cập không thuộc canonical managed set | Fail closed bằng `409 DB_001`; không tự sửa dữ liệu |
| EF-05 | Permission không tồn tại | Trả `404 IAM_004` |
| EF-06 | Permission code không hợp lệ | Trả `400 IAM_001` |
| EF-07 | Permission code dùng namespace `ROLE_` | Trả `400 IAM_002` |
| EF-08 | Permission code trùng case-insensitive | Trả `409 IAM_005` |
| EF-09 | Permission đang được role sử dụng | Trả `409 IAM_006` |
| EF-10 | Pagination hoặc sort không hợp lệ | Trả `400 SYS_002` hoặc `SYS_003` |
| EF-11 | Concurrent create cùng logical code | Một request thành công; request còn lại trả `409 IAM_005` |
| EF-12 | Concurrent delete và assign | Cùng khóa Permission row: assign commit trước thì delete trả `409 IAM_006`; delete commit trước thì assign trả `404 IAM_004`; residual delete FK violation trả `409 IAM_006`; không có dangling reference |
| EF-13 | Redis/PostgreSQL authentication dependency lỗi | Fail closed theo authentication error contract hiện hành |

## 7. Business Rules and Validation

### Role Rules

1. Canonical role set có đúng sáu code đã xác nhận.
2. Role code lưu và trả về bằng uppercase.
3. Không có role mutation endpoint.
4. Role metadata chỉ đọc trong UC này.
5. Dữ liệu role ngoài canonical set là invariant violation, không phải dữ liệu được UC tự động sửa.
6. PostgreSQL trigger phải từ chối `UPDATE role_name` và `DELETE` đối với sáu canonical role; trigger không chặn mutation trên `role_permissions`.

### Permission Code Rules

- Format: `UPPER_SNAKE_CASE`.
- Regex: `^[A-Z][A-Z0-9_]*$`.
- Normalize khi create: `trim().toUpperCase(Locale.ROOT)`.
- Độ dài: từ 1 đến 150 ký tự sau normalization.
- Unique không phân biệt hoa/thường.
- Immutable sau create.
- Không được bắt đầu bằng `ROLE_`, nhằm bảo vệ namespace Spring role authority.
- API response và database luôn dùng canonical uppercase.

Ví dụ hợp lệ: `USER_READ`, `ORDER_APPROVE`, `IAM_PERMISSION_MANAGE`.

### Metadata Rules

- `description`: tùy chọn, trim; blank được lưu thành `null`; tối đa 500 ký tự.
- Permission chỉ hiển thị bằng canonical `code` và `description`; không có tên hiển thị riêng hoặc logic sinh tên tiếng Anh/tiếng Việt.
- Update chỉ chấp nhận `description` và sử dụng last-write-wins.
- Update request chứa `code` hoặc unknown property phải bị từ chối với HTTP 400.

### Pagination and Sorting Rules

- `page`: zero-based, mặc định `0`, không âm.
- `size`: mặc định `20`, tối thiểu `1`, tối đa `100`.
- `search`: optional; search case-insensitive trên code và description.
- `sortBy`: mặc định `code`; allowlist gồm `code`, `description`, `id`.
- `direction`: `asc` hoặc `desc`, mặc định `asc`.
- Role list không phân trang vì tập hợp cố định có sáu phần tử; `data` là array `RoleResponse` theo canonical order.
- Permission list có phân trang và không trả trực tiếp Spring `Page`; `data` dùng `PermissionPageResponse` ổn định.

## 8. API Contracts

### Endpoints

| Method | Endpoint | Purpose | Success |
|---|---|---|---|
| `GET` | `/api/v1/iam/roles` | List canonical roles và assigned permissions | `200` |
| `GET` | `/api/v1/iam/roles/{roleId}` | Get canonical role detail | `200` |
| `GET` | `/api/v1/iam/permissions` | Search/page permissions | `200` |
| `GET` | `/api/v1/iam/permissions/{permissionId}` | Get permission detail | `200` |
| `POST` | `/api/v1/iam/permissions` | Create permission | `201` |
| `PATCH` | `/api/v1/iam/permissions/{permissionId}` | Update description | `200` |
| `DELETE` | `/api/v1/iam/permissions/{permissionId}` | Delete unused permission | `200` |
| `PUT` | `/api/v1/iam/roles/{roleId}/permissions/{permissionId}` | Idempotent assign | `200` |
| `DELETE` | `/api/v1/iam/roles/{roleId}/permissions/{permissionId}` | Idempotent unassign | `200` |

### Create Permission Request

```json
{
  "code": "order_approve",
  "description": "Cho phép xác nhận phê duyệt đơn hàng"
}
```

Code được lưu và trả về là `ORDER_APPROVE`.

### Update Permission Request

```json
{
  "description": "Mô tả đã cập nhật"
}
```

### Permission Response

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thành công",
  "data": {
    "id": "70390ea5-5c57-42dd-b393-a019c80a02f9",
    "code": "ORDER_APPROVE",
    "description": "Mô tả đã cập nhật"
  },
  "timestamp": "2026-10-20T10:00:00Z"
}
```

### Permission Page Response

Mọi trường page metadata là số nguyên không âm. `page` là zero-based; `items` dùng `PermissionResponse`.

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thành công",
  "data": {
    "items": [],
    "page": 0,
    "size": 20,
    "totalElements": 0,
    "totalPages": 0
  },
  "timestamp": "2026-10-20T10:00:00Z"
}
```

### Role Response Data

```json
{
  "id": "762245df-3573-45c5-9d99-c3ca782fba67",
  "code": "SHOP_OWNER",
  "description": "Chủ cửa hàng",
  "active": true,
  "permissions": [
    {
      "id": "70390ea5-5c57-42dd-b393-a019c80a02f9",
      "code": "ORDER_APPROVE",
      "description": "Mô tả đã cập nhật"
    }
  ]
}
```

### Success Payload Matrix

| Operation | HTTP | `ApiResponse.data` |
|---|---:|---|
| List roles | 200 | Array `RoleResponse[]`, không phân trang, canonical order |
| Get role | 200 | `RoleResponse` |
| List permissions | 200 | `PermissionPageResponse { items, page, size, totalElements, totalPages }` |
| Get permission | 200 | `PermissionResponse` |
| Create permission | 201 | `PermissionResponse` |
| Update permission description | 200 | `PermissionResponse` |
| Delete unused permission | 200 | `ApiResponse<Void>`; field `data` bị omitted khỏi JSON |
| Assign permission | 200 | `RoleResponse` mới nhất sau commit |
| Unassign permission | 200 | `RoleResponse` mới nhất sau commit |

Tất cả success và error response đều dùng `ApiResponse`. Request body chứa `code` trong PATCH hoặc unknown property trả `400 SYS_003`. Query parameter/page/sort không hợp lệ trả `400 SYS_002`.

Delete Permission thành công dùng running `ApiResponse<Void>` và `200 OK`. Vì `data` mang giá trị null và `ApiResponse.data` áp dụng `@JsonInclude(NON_NULL)`, JSON phải omit hoàn toàn field `data`; không thay đổi `ApiResponse` hoặc Jackson configuration:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thành công",
  "timestamp": "2026-10-20T10:00:00Z"
}
```

### Error Codes

| Code | HTTP Status | Condition |
|---|---:|---|
| `IAM_001` | 400 | Permission code không đúng format |
| `IAM_002` | 400 | Permission code dùng namespace `ROLE_` |
| `IAM_003` | 404 | Role không tồn tại |
| `IAM_004` | 404 | Permission không tồn tại |
| `IAM_005` | 409 | Permission code đã tồn tại |
| `IAM_006` | 409 | Permission đang được role sử dụng |
| `SYS_002` | 400 | Query parameter không hợp lệ |
| `SYS_003` | 400 | Request body validation thất bại |
| `AUTH_001` | 401 | Chưa xác thực hoặc token/session không hợp lệ |
| `AUTH_002` | 403 | Không có `ROLE_SHOP_OWNER` |
| `DB_001` | 409 | Integrity violation không ánh xạ được sang lỗi cụ thể |

Delete thành công dùng `200 ApiResponse<Void>` để giữ response envelope thống nhất; `204` không được dùng vì không thể mang response body. Assertion contract phải xác nhận `success=true`, `code`, `message`, `timestamp` hiện diện và JSON path `$.data` không tồn tại.

## 9. Data Model and Relationships

### Role

- Bảng: `public.roles`.
- Primary key: `role_id UUID`.
- `role_name` là canonical role code.
- UC chỉ đọc role metadata.

### Permission

- Bảng: `public.permissions`.
- Primary key: `id UUID`.
- `code VARCHAR(150)` là uppercase canonical value, unique case-insensitive và immutable.
- `description VARCHAR(500) NULL`.
- Không bổ sung cột tên hiển thị hoặc cột version.

### Role Permission

- Bảng: `public.role_permissions`.
- Composite primary key: `(role_id, permission_id)`.
- Foreign keys tới `roles` và `permissions` dùng `ON DELETE RESTRICT`.
- Quan hệ nằm hoàn toàn trong IAM module; không tạo liên kết JPA entity chéo module.

### Production Provisioning

- Không sửa V1 migration.
- Forward migration insert canonical roles còn thiếu bằng stable UUID literals.
- Canonical role đã tồn tại hợp lệ được giữ nguyên, bao gồm UUID hiện tại; UUID cố định chỉ áp dụng cho row còn thiếu được migration insert.
- Migration không overwrite hoặc xóa role hiện hữu và không tác động role ngoài managed canonical dataset.
- Migration phải fail rõ ràng nếu phát hiện case-insensitive conflict, duplicate hoặc dữ liệu không tương thích với canonical role invariant; không tự sửa dữ liệu.
- FEATURE-009 dev initializer không được xem là production provisioning.
- Verification migration phải bao gồm fresh database và database đã có compatible canonical roles. Nếu existing production data không thể được mô phỏng an toàn trong automated test, phải ghi limitation và yêu cầu kiểm chứng trên inventory thực tế; không giả định production đang trống.

| Canonical Role | UUID dùng khi insert row còn thiếu | Inserted `description` | Inserted `is_active` |
|---|---|---|---:|
| `SHOP_OWNER` | `10000000-0000-4000-8000-000000000001` | `Chủ cửa hàng` | `true` |
| `SALES_STAFF` | `10000000-0000-4000-8000-000000000002` | `Nhân viên bán hàng` | `true` |
| `ACCOUNTANT` | `10000000-0000-4000-8000-000000000003` | `Kế toán` | `true` |
| `TECHNICAL_STAFF` | `10000000-0000-4000-8000-000000000004` | `Nhân viên kỹ thuật` | `true` |
| `SHIPPER` | `10000000-0000-4000-8000-000000000005` | `Nhân viên giao hàng` | `true` |
| `CUSTOMER` | `10000000-0000-4000-8000-000000000006` | `Khách hàng` | `true` |

Compatibility predicate: một existing row compatible khi `role_name` khớp chính xác một canonical UPPER_SNAKE_CASE name. Với row compatible, migration giữ nguyên `role_id`, `description` và `is_active`; không rewrite primary key hoặc metadata. Case-insensitive variant, duplicate hay collision với UUID literal dự kiến cho row khác làm migration fail trước mutation. Role ngoài managed canonical dataset không bị sửa hoặc xóa.

Forward migration phải tạo PostgreSQL trigger bảo vệ sáu canonical `role_name` khỏi `UPDATE role_name` và `DELETE`, kể cả direct SQL. Trigger không chặn update metadata khác hoặc insert/delete trong `role_permissions`, không dựa vào hard-coded JPA relationship và toàn bộ implementation thuộc IAM schema/migration scope.

### Existing Permission Data Safety

- Trước mọi schema/data mutation, migration preflight toàn bộ `public.permissions.code`.
- Mỗi code hiện hữu phải khớp `^[A-Z][A-Z0-9_]*$`, dài 1–150, không bắt đầu bằng `ROLE_` và unique case-insensitive.
- Invalid code hoặc case-insensitive conflict làm toàn bộ migration fail trước khi thay đổi schema/data.
- Migration không rename, delete hoặc normalize permission hiện hữu; không thêm tên hiển thị riêng hoặc version.
- Migration verification bao phủ fresh database, compatible existing permissions và invalid/conflicting existing permissions.

## 10. Consistency and Concurrency

- Permission create dựa vào unique index làm protection cuối cho concurrent duplicate.
- Delete và assign cùng khóa pessimistic trên target Permission row trong transaction; lock order là Permission trước Role khi cả hai cần khóa.
- Assign/unassign khóa role target bằng pessimistic write lock trong transaction sau khi đã khóa Permission row.
- Composite primary key bảo vệ duplicate assignment.
- Delete kiểm tra assignment trước, sau đó delete và flush trong cùng transaction.
- FK `ON DELETE RESTRICT` bảo vệ race giữa delete và assign.
- Permission đang được assign cho ít nhất một role không được xóa: trả `409 IAM_006`, không auto-unassign, không partial delete và giữ nguyên permission cùng toàn bộ mappings.
- Trong delete-versus-assign: nếu assign commit trước, delete quan sát mapping và trả `409 IAM_006`; nếu delete commit trước, assign quan sát permission không còn tồn tại và trả `404 IAM_004`. FK/constraint violation còn lại trên delete path phải được map chính xác thành `409 IAM_006`, không trả `DB_001` hoặc HTTP 500.
- Known constraint violation khác được ánh xạ sang IAM error; lỗi không xác định ngoài delete-versus-assign fallback `DB_001`.
- Permission description update không dùng optimistic locking và có semantics last-write-wins; request/response/schema không chứa version.
- Không giữ transaction hoặc database lock qua external call.

## 11. JWT and Redis Consistency

JWT chỉ mang identity/session claims. Redis chỉ quản lý session state. Authority được resolve từ PostgreSQL trên mỗi protected request theo chuỗi:

```text
JWT validation
  → Redis session validation
  → PostgreSQL current user + roles + permissions
  → GrantedAuthority resolution
  → @PreAuthorize
```

Vì vậy, assign/unassign có hiệu lực từ request kế tiếp sau commit mà không cần login lại, refresh JWT hoặc xóa Redis authority cache. Request đã bắt đầu trước thời điểm commit tiếp tục sử dụng authentication context của chính request đó.

## 12. Acceptance Criteria

- **AC-01:** Given SHOP_OWNER hợp lệ, when list roles, then hệ thống trả đúng sáu canonical role theo canonical order.
- **AC-02:** Given bất kỳ actor nào, when gọi API create/delete/rename/update role, then endpoint đó không tồn tại.
- **AC-03:** Given non-SHOP_OWNER đã xác thực, when gọi endpoint của UC, then hệ thống trả `403 AUTH_002`.
- **AC-04:** Given anonymous hoặc invalid token, when gọi endpoint, then hệ thống trả `401 AUTH_001`.
- **AC-05:** Given code hợp lệ có whitespace/lowercase, when create, then code được trim, chuyển uppercase, lưu và trả về uppercase.
- **AC-06:** Given code chỉ khác case với code hiện hữu, when create, then hệ thống trả `409 IAM_005`.
- **AC-07:** Given code không đúng `UPPER_SNAKE_CASE` sau normalization, when create, then hệ thống trả `400 IAM_001`.
- **AC-08:** Given code bắt đầu bằng `ROLE_`, when create, then hệ thống trả `400 IAM_002`.
- **AC-09:** Given description dài hơn 500, when create/update, then hệ thống trả `400 SYS_003`.
- **AC-10:** Given permission tồn tại, when update metadata, then chỉ description thay đổi theo last-write-wins và code giữ nguyên.
- **AC-11:** Given create/update/response/schema, when xử lý permission, then không có trường tên hiển thị riêng hoặc version.
- **AC-12:** Given PATCH payload chứa code, when update, then hệ thống trả HTTP 400 và code không đổi.
- **AC-13:** Given permission chưa gán, when delete, then hệ thống trả 200 và xóa permission.
- **AC-14:** Given permission đang gán cho ít nhất một role, when delete, then hệ thống trả `409 IAM_006`, không auto-unassign và integration test xác nhận permission cùng mọi role assignment vẫn nguyên vẹn.
- **AC-15:** Given role và permission hợp lệ, when assign, then đúng một mapping tồn tại.
- **AC-16:** Given mapping đã tồn tại, when assign lại, then thao tác thành công và không tạo duplicate.
- **AC-17:** Given mapping tồn tại, when unassign, then mapping bị xóa nhưng permission vẫn tồn tại.
- **AC-18:** Given mapping không tồn tại, when unassign, then thao tác thành công idempotently.
- **AC-19:** Given user có active session và mang role target, when permission được assign, then request kế tiếp có authority mới mà không login lại.
- **AC-20:** Given user có active session và mang role target, when permission được unassign, then request kế tiếp không còn authority đó.
- **AC-21:** Given concurrent create cùng logical code, when transactions commit, then chỉ một permission row tồn tại.
- **AC-22:** Given concurrent assign cùng mapping, when transactions commit, then chỉ một mapping tồn tại.
- **AC-23:** Given concurrent delete và assign, when transactions kết thúc, then không có dangling reference hoặc partial commit.
- **AC-24:** Given bất kỳ mutation của UC, when transaction commit, then `user_roles` không thay đổi.
- **AC-25:** Given fresh database hoặc database đã có compatible canonical roles, when forward migration chạy, then đủ sáu canonical role tồn tại; role hiện hữu hợp lệ giữ nguyên UUID và role thiếu được insert bằng UUID cố định trong migration.
- **AC-26:** Given case-insensitive conflict, duplicate hoặc canonical role data không tương thích, when migration chạy, then migration fail rõ ràng; không overwrite/xóa role hiện hữu và không tác động role ngoài managed canonical dataset.
- **AC-27:** Given Redis hoặc PostgreSQL authentication dependency lỗi, when truy cập API, then hệ thống fail closed.
- **AC-28:** Given page size lớn hơn 100 hoặc sort field ngoài allowlist, when list permissions, then hệ thống trả HTTP 400.
- **AC-29:** Given canonical role, when direct SQL cố rename `role_name` hoặc delete row, then PostgreSQL trigger từ chối; assign/unassign permission vẫn thành công theo contract.
- **AC-30:** Given existing compatible canonical role, when migration chạy, then UUID, description và active state được giữ nguyên; given case variant/duplicate/UUID collision không tương thích, then migration fail trước mutation.
- **AC-31:** Given existing permission data, when migration preflight chạy, then invalid/noncanonical/conflicting codes làm migration fail trước mutation và compatible codes không bị normalize, rename hoặc delete.
- **AC-32:** Given list permissions, when request hợp lệ, then `ApiResponse.data` đúng shape `{items,page,size,totalElements,totalPages}` và không expose Spring `Page`; role list trả array không phân trang.
- **AC-33:** Given delete-versus-assign race, when assign thắng thì delete trả `409 IAM_006`; khi delete thắng thì assign trả `404 IAM_004`; mọi residual FK violation ở delete path được map `IAM_006`, không `DB_001`/500.
- **AC-34:** Given permission chưa được assign, when delete thành công, then response là `200 ApiResponse<Void>` với `success`, `code`, `message`, `timestamp` theo API conventions và JSON không chứa field `data`.

## 13. Dependencies

- FEATURE-006 IAM persistence baseline.
- FEATURE-007 authentication/JWT/Redis/current-authority baseline.
- `ApiResponse`, `GlobalExceptionHandler` và common error conventions.
- PostgreSQL role inventory ở target environment để kiểm chứng migration với existing production data.
- Spec Review approval trước khi tạo active implementation plan.

FEATURE-009 không phải dependency và không được sửa bởi UC này.

## 14. Security Notes and Open Decisions

- Durable audit infrastructure chưa tồn tại. UC chỉ có thể ghi structured operational log chứa actor user ID, operation, role ID, permission ID/code và outcome; log này không được tuyên bố là immutable audit trail.
- UC Manage User Accounts and Status phải ngăn non-owner tự gán `SHOP_OWNER`. Quy tắc có cho phép gỡ SHOP_OWNER cuối cùng hay không chưa thuộc phạm vi và cần business decision riêng.
- `RESOLVED — Canonical role protection`: forward migration tạo PostgreSQL trigger cấm rename/delete sáu canonical roles; assignment mapping không bị chặn.
- `RESOLVED — UUID and compatibility`: bảng UUID literal và exact-name compatibility predicate ở §9 là contract chính thức; existing compatible row giữ nguyên identity/metadata.
- `RESOLVED — Existing permission safety`: preflight validation chạy trước mutation và fail trước invalid/conflicting code; không tự sửa dữ liệu.
- `RESOLVED — HTTP pagination`: permission page dùng DTO ổn định, role list không phân trang và mọi response nằm trong `ApiResponse`.
- `RESOLVED — Delete-versus-assign`: hai path serialize trên Permission row và có deterministic error mapping như §10.
- `RESOLVED — Delete success JSON`: giữ running `ApiResponse<Void>`; null data bị omitted bởi `NON_NULL`, không đổi envelope/Jackson và test phải assert `$.data` không tồn tại.
- `RESOLVED — Permission display`: permission chỉ có canonical `code` và optional `description`; không thêm tên hiển thị riêng hoặc migration liên quan.
- `RESOLVED — Delete assigned permission`: trả `409 IAM_006`, không auto-unassign và không partial delete.
- `RESOLVED — Canonical roles provisioning`: forward migration quản lý sáu canonical codes, giữ nguyên role hợp lệ, insert role thiếu bằng UUID cố định và fail trước conflict/incompatible data.
- `RESOLVED — Concurrency`: description update dùng last-write-wins; không thêm entity/schema/API versioning. Chỉ xem xét lại khi có nhiều owner/admin cùng quản trị.
- Limitation cần ghi nhận nếu automated test không thể mô phỏng an toàn existing production role data; trước rollout phải kiểm chứng bằng inventory thực tế, không giả định production trống.
