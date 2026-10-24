# WORK-014 — Authenticated User Profile

## Metadata

- **Work ID**: `WORK-014`
- **Feature / Use Case**: Lấy thông tin tài khoản của người dùng đang đăng nhập
- **Version**: `0.2.0`
- **Owner**: Product Owner / IAM owner
- **Owning Module**: `iam`
- **Risk Reasons**: `security`, `public contract`
- **Design Review**: `APPROVED`
- **Decision Owner**: Product Owner / Tech Lead / Security Owner

## 1. Goal and Scope

### Problem and Desired Outcome

Client cần một endpoint chuẩn để lấy danh tính và các role hiện hành của người dùng từ access session đã xác thực, thay vì suy luận từ dữ liệu login đã cache hoặc tự giải mã JWT. Kết quả phải phản ánh dữ liệu IAM hiện hành và tuân thủ envelope `ApiResponse<T>`.

### In Scope

- Bổ sung `GET /api/v1/auth/me` trong module `iam`.
- Xác thực bằng access JWT qua Spring Security Resource Server hiện có.
- Trả về `id`, `username`, `email` và danh sách active role names đã chuẩn hóa.
- Ánh xạ chính xác các lỗi authentication, account status và auth infrastructure hiện có.
- Bổ sung kiểm thử contract, security và integration tương ứng.
- Sửa lỗi tối thiểu trong `RedisSessionStore.revokeSession(...)`: truyền đủ ba Redis keys mà `revoke-session.lua` yêu cầu, gồm session key, revoked tombstone key và `buildUserSessionsKey(userId)`.
- Bổ sung regression test chứng minh logout/revocation xóa session khỏi user-session index mà không gây lỗi Lua và không thay đổi semantics thu hồi session hiện có.

### Out of Scope

- Thay đổi login, refresh, JWT claims, TTL, rotation hoặc Redis session model ngoài bugfix tương thích tham số `revokeSession` đã nêu trong In Scope.
- Thay đổi Lua algorithm, Redis key naming, TTL, tombstone hoặc revocation semantics ngoài việc truyền đúng user-session index key đã tồn tại.
- Thêm permission riêng cho `/auth/me`; mọi người dùng đã xác thực và còn `ACTIVE` đều được phép gọi.
- Trả về permissions, thông tin hồ sơ mở rộng, token hoặc dữ liệu nhạy cảm khác.
- Thay đổi schema, migration, public Java API hoặc domain event.
- Sửa hoặc mở lại phạm vi lịch sử của FEATURE-007; WORK-014 là feature mở rộng độc lập.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role/Permission | Data Restrictions |
|---|---|---|---|
| Người dùng đã xác thực và tài khoản `ACTIVE` | Đọc danh tính của chính mình | Access JWT hợp lệ; không yêu cầu role/permission cụ thể | Chỉ trả dữ liệu của UUID trong JWT `sub` |
| Anonymous hoặc token không hợp lệ | Không được truy cập | Not applicable | Không trả dữ liệu người dùng |

- Không nhận user ID từ path, query hoặc body; danh tính chỉ lấy từ access JWT đã được security chain xác thực.
- Không ghi JWT, session ID, password hash, token hoặc dữ liệu credential vào log hay response.
- Access authentication hiện kiểm JWT, Redis session và trạng thái/authorities từ PostgreSQL trước controller. Endpoint không được bỏ qua hoặc tái hiện thực các kiểm tra này.
- Dữ liệu profile được tải theo UUID trong JWT `sub`; không tin email hoặc roles từ token vì access JWT hiện không chứa các claim đó.
- Nếu user biến mất sau khi token được cấp, request phải thất bại với `401 AUTH_001`; không biến thành `404` để tránh làm lộ trạng thái tài khoản.
- Nếu user không còn `ACTIVE`, trả `403 AUTH_008`. Nếu Redis/PostgreSQL không khả dụng trong authentication/profile resolution, trả `503 AUTH_009` và fail closed.

## 3. Business Rules

1. Endpoint chỉ trả thông tin của authenticated subject hiện tại.
2. `id` là UUID của `User.id`; `username` và `email` lấy từ bản ghi PostgreSQL hiện hành.
3. `roles` chỉ gồm active role names hợp lệ, không có prefix `ROLE_`, tuân thủ cùng normalization/collision rules của login response và được sắp xếp ổn định.
4. Không trả permission authorities trong `roles`.
5. Mọi response của `GET /api/v1/auth/me`, gồm success và error phát sinh tại controller, phải có `Cache-Control: no-store`; không được lưu authenticated profile để tái sử dụng sau logout, đổi tài khoản hoặc thay đổi role. Authentication error bị chặn trước controller tiếp tục tuân theo security error handler hiện có và phải được kiểm tra riêng về cache header trong implementation review.
6. Không tạo session mới, không rotate token và không thay đổi trạng thái Redis khi đọc profile.
7. `revokeSession` phải gọi Lua script với đúng ba keys theo contract hiện có. Key thứ ba phải là `buildUserSessionsKey(userId)` để `SREM` loại session khỏi user-session index; bugfix không được đổi TTL, tombstone hoặc logout/revocation semantics.

## 4. Data and Migration Decisions

- Owned entities/tables: đọc `users`, `user_roles`, `roles` và dữ liệu liên quan cần thiết trong module `iam`.
- Cross-module references: Not applicable.
- Additive/destructive migration impact: Not applicable.
- Existing-data backfill/rollback/recovery: Not applicable.
- Index, uniqueness and database integrity: không thay đổi.
- Redis persistence contract: không thêm key namespace hoặc dữ liệu mới; chỉ sửa Java invocation cho khớp contract ba keys của Lua script hiện có.

## 5. Contracts

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| `GET /api/v1/auth/me` | `Authorization: Bearer <access_token>`; authenticated user phải còn `ACTIVE` | Không có body, path parameter hoặc query parameter | `200`, `ApiResponse<CurrentUserResponse>` | `401 AUTH_001`, `401 AUTH_007`, `403 AUTH_008`, `503 AUTH_009` theo nguyên nhân hiện có |

Success body:

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thành công",
  "data": {
    "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
    "username": "admin",
    "email": "admin@example.com",
    "roles": ["SHOP_OWNER"]
  },
  "timestamp": "2026-10-24T00:00:00Z"
}
```

Error body khi thiếu, sai hoặc hết hạn access token:

```json
{
  "success": false,
  "code": "AUTH_001",
  "message": "Chưa xác thực hoặc phiên đăng nhập đã hết hạn",
  "timestamp": "2026-10-24T00:00:00Z"
}
```

Contract details:

- JSON field names giữ camelCase: `id`, `username`, `email`, `roles`.
- `roles` luôn là JSON array; trả `[]` nếu user không có active role hợp lệ.
- `data` không chứa access token, refresh token, permissions, password hash, phone, provider, full name hoặc avatar.
- `timestamp` do `ApiResponse` sinh dưới dạng `Instant` theo cấu hình Jackson hiện hành.
- Response header bắt buộc: `Cache-Control: no-store`. Không bổ sung `Pragma` hoặc `Expires` trong contract này vì không có yêu cầu hỗ trợ HTTP/1.0 cache legacy; nếu phát sinh yêu cầu tương thích mới phải cập nhật spec trước implementation.
- `GET /api/v1/auth/me` là protected route; security default `authenticated()` hiện đã bảo vệ route, nhưng cần test regression cụ thể để ngăn whitelist nhầm trong tương lai.

### Inter-Module Java API

Not applicable. REST payload là implementation detail trong `iam/internal/payload/response`; không tạo Java API công khai chỉ để phục vụ HTTP endpoint này.

### Domain Event

Not applicable.

## 6. Concurrency, Transaction and Failure Safety

- Đây là read-only operation; không yêu cầu lock hoặc write transaction.
- Service/profile resolution phải dùng transaction read-only khi truy cập lazy role graph hoặc repository query tương ứng.
- Security chain hiện tải user/authorities từ PostgreSQL trước controller. Việc profile resolution có thể phát sinh thêm một lần đọc DB; không thay đổi authentication principal hoặc JWT contract chỉ để tối ưu query trong phạm vi này.
- Nếu user/status/roles thay đổi giữa bước authentication và profile query, kết quả không được mở rộng quyền. Service phải kiểm tra user còn tồn tại và `ACTIVE`; role response dùng snapshot hiện hành của lần đọc profile.
- Database/Redis failure phải fail closed bằng contract auth hiện có, không trả profile cũ và không hạ lỗi thành `AUTH_001` nếu nguyên nhân là outage đã nhận diện.
- Không có retry/idempotency requirement vì endpoint chỉ đọc và không tạo side effect.
- Logout/revocation phải giữ tính nguyên tử của Lua script hiện có. Java caller phải truyền đủ `KEYS[1..3]`; không sửa script để bỏ cleanup user-session index và không fail open khi Redis lỗi.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Access JWT hợp lệ của user `ACTIVE` gọi `GET /api/v1/auth/me` | Security integration test với PostgreSQL và Redis test environment hiện có | HTTP `200`; envelope `SUCCESS`; trả đúng `id`, `username`, `email`, `roles` |
| AC-02 | Request không có Authorization header | Security integration test | HTTP `401`; `success=false`; `code=AUTH_001`; không có `data` |
| AC-03 | Bearer token malformed, sai signature/audience/type hoặc hết hạn | Security integration test theo JWT baseline | HTTP `401 AUTH_001`; không vào controller và không lộ chi tiết token |
| AC-04 | Access token còn hạn thuộc session đã revoked | Redis/security integration test | HTTP `401 AUTH_007` theo semantics hiện có |
| AC-05 | User không tồn tại hoặc không còn `ACTIVE` | Integration test thay đổi PostgreSQL giữa các request | Missing user trả `401 AUTH_001`; inactive user trả `403 AUTH_008` |
| AC-06 | Role thay đổi sau khi token được cấp | Integration test dùng lại cùng access token sau khi cập nhật role | Request kế tiếp trả danh sách role hiện hành, đã lọc/chuẩn hóa/sắp xếp giống login response; không cần reissue token |
| AC-07 | User không có active role hợp lệ | Service/controller integration test | `roles` là `[]`, không phải `null`; request vẫn `200` nếu user hợp lệ và `ACTIVE` |
| AC-08 | PostgreSQL hoặc Redis unavailable | Failure-injection integration test phù hợp lớp lỗi | HTTP `503 AUTH_009`; không fail open và không trả profile |
| AC-09 | Response privacy boundary | JSON assertions và log/credential scan | Không có token, session ID, password hash, permissions hoặc trường profile ngoài contract |
| AC-10 | Kiến trúc và regression | Targeted tests, IAM security suite, full suite và boundary scan | Không schema migration, không cross-module internal import, login/refresh/logout vẫn giữ hành vi hiện có |
| AC-11 | Authenticated profile response không được cache | Contract/security integration tests trên success và các error path áp dụng | Header chứa `Cache-Control: no-store`; không dựa vào browser default hoặc cache heuristic |
| AC-12 | Logout/revocation Java invocation khớp Lua contract | Redis integration/regression test kiểm tra `revokeSession`, logout và user-session index | Lua nhận đúng ba keys; logout thành công; session bị revoke và bị `SREM` khỏi `buildUserSessionsKey(userId)`; Redis failure vẫn fail closed |

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| WORK-014 có được tạo dù `.agent/` hiện chưa được Git ignore đúng như governance mô tả hay không | Repository owner | Planning artifact creation | `RESOLVED` — người dùng xác nhận tiếp tục ngày 2026-10-24; không sửa `.gitignore` trong planning scope |
| `/api/v1/auth/me` có phải thay đổi phạm vi FEATURE-007 hay feature độc lập | Product Owner | Planning | `RESOLVED` — feature mở rộng độc lập `WORK-014`; giữ nguyên FEATURE-007 |
| Endpoint có cần permission riêng ngoài authenticated state | Product Owner / Security Owner | Design Review | `RESOLVED` — không; mọi user `ACTIVE` với access session hợp lệ được đọc chính mình |
| Nguồn dữ liệu profile và role | IAM owner | Design Review | `RESOLVED` — PostgreSQL hiện hành theo JWT `sub`; role semantics giống login response; không thêm JWT claims |
| Cache policy cho authenticated profile | Product Owner / Security Owner | Design Re-review | `RESOLVED` — bắt buộc `Cache-Control: no-store`; không yêu cầu legacy `Pragma`/`Expires` trong contract hiện tại |
| Xử lý mismatch Java 2 keys/Lua 3 keys trong `revokeSession` | Repository owner / IAM owner | Design Re-review | `RESOLVED` — chọn Hướng 1 ngày 2026-10-24: mở rộng WORK-014 cho bugfix tối thiểu truyền `buildUserSessionsKey(userId)` làm key thứ ba; giữ nguyên Lua cleanup và logout semantics |
| Full-suite baseline exception | Repository owner | Trước Review | `RESOLVED` — không chấp nhận ngoại lệ chỉ dựa trên `git blame`; AC-10 tiếp tục yêu cầu repository-wide full suite PASS trên trạng thái code cuối cùng |

Không còn quyết định sản phẩm mở cản trở Design Review. CODEX_REVIEW cần xác minh security/error semantics, read consistency và test matrix trước khi chuyển Delivery Brief sang `READY`.
