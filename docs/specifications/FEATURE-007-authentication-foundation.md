# Authentication Foundation Feature Specification

SPEC_ID: FEATURE-007
SPEC_VERSION: 0.3.1
SPEC_STATUS: DRAFT_FOR_REVIEW

## Metadata and Governance

- **Feature ID**: `FEATURE-007`
- **Feature Name**: Authentication Foundation
- **Owner / Author**: Product Owner / IAM team — người chịu trách nhiệm cuối cùng chưa được chỉ định
- **Owning Module**: `iam`
- **Repository Scope**: Backend, `D:\DO_AN\Aquasuite\aquasuite-be`
- **Target Package**: `com.aquasuite.ims.module.iam`; HTTP payload và authentication logic thuộc `internal/`. Không tạo public Java API liên module khi chưa có consumer.
- **Runtime Baseline**: Java 21, Spring Boot 3.5.16, PostgreSQL
- **Spec Review Status**: `PENDING`; bản này chưa được phê duyệt
- **Risk Level**: `CRITICAL`, vì xử lý mật khẩu, phiên và phân quyền
- **Revision Basis**: Các quyết định Product Owner cuối cùng cho B3, B5, W3, OD-14 và OD-15; giữ các quyết định trước đó cho B1, B2, B4, W1, W2, W4–W6 và S1.

## 1. Scope and Current Baseline

FEATURE-006 đã cung cấp persistence foundation cho nhân viên nội bộ. FEATURE-007 chỉ đặc tả ba endpoint sau:

| Endpoint | Purpose | HTTP Security |
|---|---|---|
| `POST /api/v1/auth/login` | Xác minh email/password, tạo Redis session và cấp JWT | Public |
| `POST /api/v1/auth/refresh` | Xoay vòng refresh JWT một lần, cấp cặp JWT mới | Public; service tự xác minh refresh JWT |
| `POST /api/v1/auth/logout` | Thu hồi session của access JWT hiện tại | Yêu cầu access JWT hợp lệ |

Ngoài phạm vi: `/me`, logout-all, concurrent-session limit, registration/customer login, activation, reset password, OAuth2 social login, 2FA và rate limiter triển khai trong ứng dụng. Không thêm PostgreSQL session table, không sửa schema hoặc migration V1.

### Verified Source Boundary

- Flyway `V1__create_iam_foundation.sql` tạo đúng năm bảng `public.users`, `roles`, `permissions`, `user_roles`, `role_permissions`. `users.username` là `varchar(100)`, `email` là `varchar(320)`, `password_hash` là `varchar(255)`; không có `name`/`full_name`. Hai join tables có composite PK và không chứa assignment metadata.
- `UserEntity`, `RoleEntity`, `PermissionEntity` là ba JPA entities. `UserEntity.roles` và `RoleEntity.permissions` là unidirectional lazy many-to-many. `UserStatus` gồm `PENDING_ACTIVATION`, `ACTIVE`, `INACTIVE`, `LOCKED`; `RoleEntity` có `roleName` và `isActive`, `PermissionEntity` có `code`.
- `UserRepository` hiện có `findByEmailIgnoreCase` nhưng chưa có authentication query/`@EntityGraph`. FEATURE-007 giữ nguyên schema và entities. Nếu cần bổ sung query nạp roles/permissions, file có thể thay đổi trong implementation được duyệt là `src/main/java/com/aquasuite/ims/module/iam/internal/repository/UserRepository.java`. Không giả định `@EntityGraph` luôn tạo một SQL SELECT; đo query count và kiểm soát lazy loading.
- `ApiResponse<T>` có factory `ok`/`error`; `GlobalExceptionHandler` dùng `ApiResponse.error(...)`. `CommonErrorCode.AUTH_001` là 401, thông điệp hiện có "Chưa xác thực hoặc phiên đăng nhập đã hết hạn"; `AUTH_002` là 403, thông điệp "Bạn không có quyền thực hiện thao tác này". Không có lớp `ErrorResponse`.
- `pom.xml` hiện chỉ có direct dependency `spring-security-core`, chưa có Resource Server/Nimbus/Redis dependencies hoặc `SecurityFilterChain`. `application.yml`, `application-dev.yml`, `application-prod.yml` và `application-test.yml` chưa cấu hình Swagger/OpenAPI security matcher. Những thay đổi implementation/dependency/config cần plan và approval riêng.
- Sáu IAM skeleton files thuộc ranh giới FEATURE-006 được bảo toàn: `UserExternalService`, `UserRequest`, `UserResponse`, `UserController`, `UserService`, `UserServiceImpl`. Không thêm cột `name`/`full_name`, không sửa Flyway V1 hoặc FEATURE-006 entities.

## 2. Business Rules and JWT Contract

### 2.1. Login and Credentials

1. Login nhận `email` và `password`; trim email trước tra cứu không phân biệt hoa/thường. Không trim hoặc biến đổi password. So khớp với `password_hash` bằng `BCryptPasswordEncoder` strength 12.
2. Email không tồn tại và password sai có cùng public error `AUTH_003` (401) và cùng thông điệp. Xử lý trường hợp email không tồn tại với chi phí xác minh tương đương để hạn chế timing enumeration. Không ghi email/password/hash/JWT vào security log ngoài thông tin tối thiểu cần vận hành.
3. Chỉ `UserStatus.ACTIVE` được login/refresh/truy cập protected route. Sau khi credential hoặc JWT được xác minh, user ở `PENDING_ACTIVATION`, `INACTIVE` hoặc `LOCKED` nhận `AUTH_008` (403); không dùng `AUTH_001` cho user đã xác thực nhưng bị cấm.
4. Login thành công ghi Redis session trước khi trả cặp token. Login response chứa `user.id`, `user.username`, `user.email`, `user.roles` hiện hành; không có `user.name`.

### 2.2. Signed Tokens

| JWT | Algorithm | Issuer | Audience | `token_type` claim | Nominal TTL |
|---|---|---|---|---|---|
| Access | HS512 | `aquasuite-be` | `aquasuite-api` | `access` | 1.800 giây |
| Refresh | HS512 | `aquasuite-be` | `aquasuite-auth` | `refresh` | Tối đa 1.296.000 giây (15 ngày) |

- Cả hai là JWT, không có opaque refresh option. Required claims: `iss`, `aud`, `token_type`, `sub` (user UUID), `sid` (session UUID), `jti` (token UUID), `iat` và `exp`. Mỗi decoder/validator phải kiểm tra HS512 được cho phép, chữ ký, issuer/audience chính xác, token type đúng path, expiration và định dạng các UUID bắt buộc. JWT sai chữ ký, alg, issuer, audience, type hoặc claims bị từ chối. Access JWT không dùng để refresh; refresh JWT không đi qua access authentication path.
- JWT secret ngẫu nhiên tối thiểu 64 bytes sau decode, lấy từ cấu hình bảo mật/biến môi trường hoặc secret manager; thiếu/yếu thì startup fail. Không hardcode secret. `token_type` trong JWT là claim phân biệt access/refresh; `token_type: "Bearer"` trong HTTP response là scheme, không thay thế claim.
- Login đặt `absoluteExpiresAt = loginAt + 15 ngày`. JWT cấp ở login/refresh có `exp = min(now + nominalTTL, absoluteExpiresAt)`; refresh không gia hạn mốc tuyệt đối. `expires_in` trong HTTP response là số giây sống thực tế của access JWT vừa cấp, bình thường 1.800 nhưng ngắn hơn gần deadline. Tại hoặc sau deadline, session và token không được chấp nhận.
- JWT không mang authorities làm nguồn sự thật. Nếu JWT có claim role/permission vì tương thích trong tương lai, authentication path vẫn bỏ qua claims đó khi tính quyền.
- **Error precedence (N1)**: Với JWT có chữ ký/định dạng đủ để phân loại, kiểm `exp` và absolute deadline **trước** khi đọc Redis revoked tombstone. Access JWT đã hết hạn trả `AUTH_001` (401); refresh JWT đã hết hạn trả `AUTH_005` (401), kể cả khi cùng `sid` đã revoked. `AUTH_007` chỉ dành cho JWT còn hiệu lực thời gian nhưng session đã revoked. Token sai chữ ký/claims tiếp tục bị từ chối theo mã invalid tương ứng, không dùng trạng thái Redis để biến thành revoked.

### 2.3. Session and Current Authorities

Trên **mỗi protected request**, xác minh access JWT, kiểm Redis session theo `sid` còn active, thuộc đúng `sub` và chưa vượt deadline; sau đó đọc `UserStatus`, roles, `RoleEntity.isActive` và permissions **hiện hành từ PostgreSQL** trước khi dựng authorities/SecurityContext. Chỉ user ACTIVE đi tiếp. Permission bị thu hồi và role bị vô hiệu hóa có hiệu lực ở request kế tiếp. Không thêm Redis authority cache hay dùng JWT authority cũ. Redis/PostgreSQL không khả dụng thì fail closed; không cấp quyền dựa trên dữ liệu cũ.

## 3. Redis Session and Strict Refresh Rotation

### 3.1. Redis Data Model

| Key | Value / purpose | TTL boundary |
|---|---|---|
| `auth:session:{sid}` | `userId`, `loginAt`, `absoluteExpiresAt`, `state=ACTIVE`, `currentRefreshFingerprint` | Expire tại absolute deadline |
| `auth:spent:{sid}:{fingerprint}` | Dấu đã dùng của refresh JWT cũ, `userId`, `refreshExp`; bằng chứng để phân loại replay | Không quá `refreshExp` cũ/absolute deadline |
| `auth:revoked:{sid}` | `userId`, `absoluteExpiresAt`, reason `LOGOUT` hoặc `REPLAY`; phân biệt revoked với unknown | Expire tại absolute deadline |

Fingerprint là HMAC/hash của token hoặc `jti` với server-side key; không dùng refresh token dạng rõ làm Redis key. Mọi key có TTL hữu hạn; session Redis không tồn tại sau absolute deadline. JWT `exp` và Redis expiry dùng UTC cùng mốc đã làm tròn nhất quán. Redis access cần TLS/ACL và namespace cô lập theo môi trường. Không có `Idempotency-Key`, response cache, grace period hoặc access JTI blacklist.

### 3.2. Atomic State Transition

1. Service xác minh refresh JWT HS512, `iss`, `aud=aquasuite-auth`, `token_type=refresh`, `exp`, `sub`, `sid`, `jti`; kiểm user ACTIVE từ PostgreSQL. Nếu Redis không khả dụng, fail closed.
2. Tạo cặp token kế nhiệm trong bộ nhớ nhưng **không trả cho client** trước commit. Một thao tác nguyên tử Redis (script/transaction có compare-and-set tương đương) kiểm `auth:session:{sid}` còn ACTIVE, thuộc `sub`, còn trước absolute deadline và `currentRefreshFingerprint` khớp token cũ. Nếu khớp, thay bằng fingerprint của refresh JWT mới **và** ghi `auth:spent:{sid}:{oldFingerprint}` trong cùng thao tác. Đây là điểm consume-and-replace duy nhất. Chỉ sau khi commit thành công mới trả cặp JWT mới.
3. Hai request đồng thời dùng cùng refresh JWT chỉ có tối đa một thao tác rotation thành công. Request còn lại nhìn thấy spent marker và được xử lý theo quy tắc replay dưới đây; nếu nó thu hồi session trước khi response của request thắng đến client, cặp token vừa cấp cũng mất hiệu lực. Đây là hệ quả được chấp nhận của strict rotation.
4. Không hỗ trợ recovery token khi client mất response sau commit. Client không có refresh JWT mới phải login lại. Việc gửi lại refresh JWT cũ có thể bị phân loại replay và thu hồi session; không tuyên bố phát hiện token theft chính xác tuyệt đối.

### 3.3. Rejection and Replay Classification

Chỉ một refresh JWT đã xác minh chữ ký/issuer/audience/type/claims mới được dùng để tra Redis. Kiểm expiration **trước mọi tra cứu revoked/session trong Redis**; nếu token vừa expired vừa thuộc revoked session, kết quả là Expired. Thứ tự phân loại:

| State | Evidence and action | HTTP contract |
|---|---|---|
| Invalid | JWT sai chữ ký/alg/issuer/audience/type/claims | `AUTH_004` 401; không revoke |
| Expired | JWT `exp` hoặc absolute session deadline đã qua | `AUTH_005` 401; không revoke |
| Revoked | JWT còn hiệu lực thời gian và `auth:revoked:{sid}` tồn tại cho user/session này | `AUTH_007` 401; không revoke lại |
| Current | Session ACTIVE, fingerprint hiện tại khớp | Atomic rotation; tối đa một success |
| Reused | Session ACTIVE, fingerprint không còn current và spent marker hợp lệ xác nhận **chính token này** đã được consume trong session | `AUTH_006` 401; atomically revoke đúng `sid`, ghi tombstone |
| Unknown | Không có session/tombstone/spent evidence phù hợp, hoặc fingerprint chưa từng được ghi nhận | `AUTH_004` 401; không suy diễn replay và không revoke session khác |

Spent marker là bằng chứng **token đã dùng**, không phải bằng chứng ai đang gửi lại. Retry hợp lệ bằng token cũ sau response thất lạc không thể luôn phân biệt với replay; khi rơi vào hàng Reused, session liên quan cũng bị revoke. Token hết hạn, unknown hoặc signature sai không tự động bị gọi là replay. Không có default 409 cho replay.

### 3.4. Logout and Failure Boundary

`POST /logout` lấy `sid` từ access JWT đã qua protected-request validation, không nhận refresh JWT trong body. Redis atomically thu hồi đúng current session, ghi revoked tombstone và xóa current refresh fingerprint; các access JWT có cùng `sid` bị từ chối ở request kế tiếp. Session khác của user giữ nguyên. Logout lặp lại với access JWT **chưa hết hạn** thuộc revoked session trả `AUTH_007` (401); JWT đã hết hạn trả `AUTH_001` theo N1. Không dùng access JTI blacklist mặc định.

Login chỉ thành công sau khi Redis session tồn tại. Refresh chỉ trả success sau atomic state transition; nếu Redis báo lỗi hoặc kết quả commit không xác định, không cấp token dựa trên suy đoán, phân loại lại trạng thái bằng kiểm tra Redis an toàn khi có thể. Cần fault-injection test cho mất kết nối trước/sau commit và response thất lạc. Redis outage trong login/refresh/logout/protected request trả 503, không fail open.

## 4. Authority Mapping and RBAC

Nguồn authority là PostgreSQL hiện tại:

| Source | Rule | Example |
|---|---|---|
| `RoleEntity.roleName` với `isActive=true` | Trim, uppercase bằng `Locale.ROOT`; nếu chưa bắt đầu bằng `ROLE_` thì thêm đúng một `ROLE_` | `" admin "` → `ROLE_ADMIN`; `"role_admin"` → `ROLE_ADMIN` |
| `PermissionEntity.code` của active role | Giữ **nguyên code** khi hợp lệ; không tự thêm `ROLE_`, không trim/uppercase để đổi nghĩa permission; prefix `ROLE_` thuộc namespace cấm phát | `"order:read"` → `order:read`; `"ROLE_ADMIN"` → không phát authority |

Role name sau trim/uppercase hợp lệ khi phần sau optional `ROLE_` không rỗng và chỉ gồm `A-Z`, `0-9`, `_`. `null`, blank, chuỗi chỉ `ROLE_` hoặc chứa ký tự khác là invalid; không tạo authority hay cấp permissions từ role invalid. Nếu hai role ID khác nhau sau chuẩn hóa tạo cùng role authority, coi là collision: **không gộp role**, không cấp role authority hoặc permission từ các role va chạm; ghi cảnh báo vận hành không chứa dữ liệu nhạy cảm. Các role khác hợp lệ vẫn được xử lý.

**Permission namespace (N2)**: Giữ nguyên `PermissionEntity.code` như trong database khi phát một permission authority hợp lệ; không đổi tên, trim, uppercase hoặc thêm `ROLE_` vào giá trị được phát. `null`/blank (sau kiểm tra whitespace) là invalid và không phát authority. Để tránh permission bị Spring hiểu là role, nếu `code.trim().toUpperCase(Locale.ROOT)` bắt đầu bằng `ROLE_`, code đó thuộc namespace dành riêng cho role và **không được phát thành bất kỳ `GrantedAuthority` nào**, kể cả permission authority. Ghi cảnh báo vận hành không chứa secret, không âm thầm chuyển permission thành role và không sửa dữ liệu. Các permission hợp lệ khác vẫn được xử lý; quyền chỉ dựa vào code bị loại trả `AUTH_002` (403). Với user chỉ có permission code `ROLE_ADMIN` mà không có role ADMIN hợp lệ, cả `hasRole('ADMIN')` và `hasAuthority('ROLE_ADMIN')` đều phải false.

Flyway V1 chỉ ràng buộc `role_name` không rỗng và unique theo `lower(role_name)`; không bảo đảm dữ liệu hiện có thỏa quy tắc authority trên. Trước implementation/deployment phải inventory role names/permissions ở môi trường cần bảo toàn, kiểm invalid/collision và đối chiếu các biểu thức `hasRole`/`hasAuthority` thực tế. Không tự sửa database hoặc `roleName` trong FEATURE-007. Endpoint nghiệp vụ phải khai báo quyền cụ thể ở feature tương ứng; user authenticated nhưng thiếu permission nhận `AUTH_002` 403.

## 5. Spring Security Architecture

- Implementation được phê duyệt sau này dùng **Spring Security OAuth2 Resource Server + Nimbus** cho access JWT. Cấu hình `JwtDecoder` HS512 với validators bắt buộc cho `iss=aquasuite-be`, `aud=aquasuite-api`, `token_type=access`, `exp`, `sub`, `sid`, `jti`; `JwtAuthenticationConverter`/authentication integration nạp Redis session và authorities từ PostgreSQL mỗi protected request. Dùng Spring Security security chain, không tự viết custom JWT Authentication Filter nếu Resource Server đáp ứng được.
- `POST /login` và `POST /refresh` là exact method/path public matchers ở tầng HTTP Security. Refresh service dùng decoder/validator riêng cho `aud=aquasuite-auth` và `token_type=refresh`; không đi qua access authentication path. `POST /logout` và mọi route khác yêu cầu access authentication. HTTP session stateless; default deny các route không được cho phép.
- `AuthenticationEntryPoint` trả `ApiResponse.error(...)` cho 401 do thiếu/sai/hết hạn access JWT hoặc revoked session. `AccessDeniedHandler` trả cùng envelope cho `AUTH_002` 403 khi thiếu permission. Lỗi 503 do Redis/PostgreSQL outage phải dùng cùng envelope và không bị biến thành 401/403; `GlobalExceptionHandler` hiện có không tự bắt lỗi xảy ra trước controller. Không giả định có lớp `ErrorResponse`.
- Swagger/OpenAPI không public mặc định ở production: tắt route tài liệu hoặc đặt dưới matcher bảo vệ riêng. Không đưa `/swagger-ui/**` hay `/v3/api-docs/**` vào whitelist production. Profile config hiện chưa có matcher, nên phải xác minh bằng security integration test trước release.

## 6. API Contract

Mọi response dùng `ApiResponse<T>` hiện có; validation lỗi dùng `FieldErrorDetail`. Token hoặc secret không xuất hiện trong error response hay log. Các giá trị token ở ví dụ là placeholder.

### 6.1. POST /api/v1/auth/login

Request: `{"email":"john.doe@aquasuite.com","password":"<password>"}`. `email`: `@NotBlank`, `@Email`, tối đa 320 ký tự. `password`: `@NotBlank`, tối đa 100 ký tự.

Success: HTTP 200, `ApiResponse<LoginResponse>`:
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Đăng nhập thành công",
  "data": {
    "access_token": "<access-jwt>",
    "refresh_token": "<refresh-jwt>",
    "token_type": "Bearer",
    "expires_in": 1800,
    "user": {
      "id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
      "username": "johndoe",
      "email": "john.doe@aquasuite.com",
      "roles": ["ADMIN"]
    }
  },
  "timestamp": "2026-10-17T15:30:00Z"
}
```

`user.roles` phản ánh active role names từ PostgreSQL; role invalid/collision bị loại theo §4. Không có `user.name`.

### 6.2. POST /api/v1/auth/refresh

Request: `{"refresh_token":"<refresh-jwt>"}`; trường bắt buộc và không blank. Không yêu cầu access JWT hoặc `Idempotency-Key`. Service kiểm refresh JWT theo §2.2 và §3.

Success: HTTP 200, `ApiResponse<TokenResponse>` với `data.access_token`, `data.refresh_token`, `data.token_type="Bearer"`, `data.expires_in` (TTL access thực tế). Mỗi refresh thành công cấp đúng một cặp token mới. Không có response cache hoặc retry response recovery.

### 6.3. POST /api/v1/auth/logout

Header: `Authorization: Bearer <access-jwt>`. Không có request body. Success: HTTP 200, `ApiResponse<Void>`, `success=true`, `code=SUCCESS`, `message="Đăng xuất thành công"`; trường `data` bỏ qua khi null.

### 6.4. Authentication Error Contract

Các code `AUTH_001`/`AUTH_002` thuộc `CommonErrorCode` hiện tại và giữ nguyên ý nghĩa. Những code mới dưới đây là **yêu cầu dự kiến của Specification**, chưa tồn tại trong source; không sửa `CommonErrorCode.java` trong revision này. Chỉ bổ sung code có ý nghĩa khác biệt cần client hoặc vận hành xử lý; invalid/expired access JWT tiếp tục dùng ý nghĩa chưa xác thực/hết phiên của `AUTH_001`.

| Scenario | HTTP | Code (existing/new) | Public message | Handler |
|---|---|---|---|---|
| Body validation lỗi ở login/refresh | 400 | `SYS_003` existing | Dữ liệu đầu vào không hợp lệ; `errors` theo field | `GlobalExceptionHandler` |
| Thiếu hoặc sai định dạng Bearer access JWT | 401 | `AUTH_001` existing | Chưa xác thực hoặc phiên đăng nhập đã hết hạn | `AuthenticationEntryPoint` |
| Access JWT sai signature/issuer/audience/type/claims | 401 | `AUTH_001` existing | Chưa xác thực hoặc phiên đăng nhập đã hết hạn | `AuthenticationEntryPoint` |
| Access JWT hết `exp` hoặc absolute deadline, dù session đã revoked | 401 | `AUTH_001` existing | Chưa xác thực hoặc phiên đăng nhập đã hết hạn | `AuthenticationEntryPoint` trước Redis revocation lookup |
| Email không tồn tại hoặc password sai | 401 | `AUTH_003` new: `INVALID_CREDENTIALS` | Email hoặc mật khẩu không chính xác | Login service/`GlobalExceptionHandler` |
| Refresh JWT invalid hoặc unknown (không đủ spent evidence) | 401 | `AUTH_004` new: `INVALID_REFRESH_TOKEN` | Refresh token không hợp lệ | Refresh service/`GlobalExceptionHandler` |
| Refresh JWT hoặc absolute session deadline hết hạn, dù session đã revoked | 401 | `AUTH_005` new: `EXPIRED_REFRESH_TOKEN` | Refresh token hoặc phiên đã hết hạn | Refresh service/`GlobalExceptionHandler` trước Redis revocation lookup |
| Spent refresh JWT được gửi lại khi session còn active | 401 | `AUTH_006` new: `REFRESH_TOKEN_REUSED` | Refresh token đã được sử dụng; phiên liên quan đã bị thu hồi | Refresh service/`GlobalExceptionHandler` |
| Access/refresh JWT **còn hiệu lực thời gian** thuộc session đã revoked | 401 | `AUTH_007` new: `SESSION_REVOKED` | Phiên đã bị thu hồi | Access: `AuthenticationEntryPoint`; refresh: service/`GlobalExceptionHandler` |
| User đã xác minh danh tính nhưng không ACTIVE (gồm pending, inactive, locked) | 403 | `AUTH_008` new: `ACCOUNT_NOT_ACTIVE` | Tài khoản không ở trạng thái hoạt động | Access: `AccessDeniedHandler`; login/refresh: service/`GlobalExceptionHandler` |
| User ACTIVE nhưng thiếu permission yêu cầu | 403 | `AUTH_002` existing | Bạn không có quyền thực hiện thao tác này | `AccessDeniedHandler` |
| Redis/PostgreSQL unavailable trong bất kỳ authentication flow nào | 503 | `AUTH_009` new: `AUTH_SERVICE_UNAVAILABLE` | Dịch vụ xác thực tạm thời không khả dụng | Security integration hoặc service error mapper |

Tất cả error bodies dùng `ApiResponse.error(code, message)` hoặc variant có `FieldErrorDetail`; không trả stack trace, `sid`, JWT hay thông tin hạ tầng. Authentication status 401; authenticated-but-forbidden 403. Không có default 409 cho replay.

## 7. Acceptance Criteria

- [ ] **AC-01**: Chỉ có Login, Refresh và Logout; Login response có `user.id`, `user.username`, `user.email`, `user.roles`, không có `user.name`; schema V1/FEATURE-006 entity/skeleton giữ nguyên.
- [ ] **AC-02**: Access/refresh là JWT HS512, `iss=aquasuite-be`, audiences riêng, `token_type` riêng và claims bắt buộc hợp lệ; refresh JWT không đi qua access path và access JWT không dùng được ở refresh.
- [ ] **AC-03**: Access TTL 1.800 giây, refresh tối đa 15 ngày; absolute session deadline 15 ngày từ login; rotation không gia hạn deadline; JWT `exp`, `expires_in` và Redis TTL nhất quán.
- [ ] **AC-04**: Login email/password sai dùng chung `AUTH_003` 401; user không ACTIVE dùng `AUTH_008` 403; password/hash/JWT không lộ qua log/error.
- [ ] **AC-05**: Mọi protected request kiểm `sid` trên Redis và status/active roles/permissions hiện hành từ PostgreSQL; thay đổi quyền/status có hiệu lực ở request kế tiếp; không dùng JWT authority claims hoặc Redis authority cache.
- [ ] **AC-06**: Refresh atomic consume-and-replace, single-use; hai request đồng thời tối đa một rotation thành công; không Idempotency-Key, response cache hoặc grace period.
- [ ] **AC-07**: Spent refresh JWT có marker và session ACTIVE gây thu hồi đúng session, kể cả retry hợp lệ sau response thất lạc; client cần login lại nếu không có token mới. Expired/unknown/invalid không bị mặc định coi là replay.
- [ ] **AC-08**: Logout bằng access JWT thu hồi đúng `sid`; mọi access/refresh JWT của session đó bị từ chối từ request sau, session khác giữ nguyên; không có access JTI blacklist mặc định.
- [ ] **AC-09**: Role authority dùng `roleName` trim/uppercase/đúng một `ROLE_`; permission authority hợp lệ giữ nguyên `code`; invalid/colliding/inactive roles không cấp authority hoặc permission từ role đó. Permission code null/blank hoặc có prefix `ROLE_` khi kiểm không phân biệt hoa/thường là invalid và không phát `GrantedAuthority`, không được đổi tên hoặc nâng thành role.
- [ ] **AC-10**: Body validation 400, authentication failure 401, authenticated-but-forbidden 403, Redis/PostgreSQL outage 503 đúng code/body `ApiResponse`; `AUTH_001`/`AUTH_002` giữ nguyên nghĩa, không có `ErrorResponse` class giả định.
- [ ] **AC-11**: Chỉ Login và Refresh public ở tầng HTTP; Logout và các route khác được bảo vệ; Swagger/OpenAPI không public mặc định ở production.
- [ ] **AC-12**: JWT signing secret lấy từ môi trường/secret manager và fail startup nếu thiếu/yếu; Redis keys có TTL hữu hạn; không lưu refresh token dạng rõ trong Redis key.
- [ ] **AC-13**: Integration tests dùng PostgreSQL và Redis thực hoặc Testcontainers tương ứng, bao gồm race/failure injection; mock-only không đủ để chứng minh atomicity.
- [ ] **AC-14**: Trước production phải có biện pháp chống brute-force/login abuse ở lớp triển khai phù hợp (ví dụ ingress/WAF hoặc cơ chế tương đương) và evidence kiểm thử; FEATURE-007 không triển khai rate limiter. Không release production khi prerequisite chưa đạt.
- [ ] **AC-15**: Đo tải/độ trễ của Redis + PostgreSQL trên mỗi protected request; kết quả không được dùng để bỏ kiểm tra session/quyền hoặc thêm authority cache ngoài quyết định PO.
- [ ] **AC-16**: Kiểm JWT hết hạn trước Redis revocation: access hết hạn trả `AUTH_001`, refresh hết hạn trả `AUTH_005`, kể cả khi `sid` đã revoked; `AUTH_007` chỉ dành cho JWT chưa hết hạn thuộc revoked session.

## 8. AC-to-Test Verification Matrix

| AC | Verification | Pass condition |
|---|---|---|
| AC-01 | Controller contract + source/schema diff | Đúng ba endpoints và JSON `user.username`; không thay V1, FEATURE-006 entities/skeleton |
| AC-02 | JWT decoder/security integration với sai alg/signature/issuer/audience/type/claims; token swap | Chỉ JWT đúng loại và claims được chấp nhận; refresh JWT trên access path nhận 401 |
| AC-03 | Clock-controlled boundary + Redis TTL inspection tại login, sau refresh và sát absolute deadline | `exp=min(nominal, absolute)`, session không sống quá 15 ngày, refresh tại deadline thất bại |
| AC-04 | Controller/service integration + log capture, từng `UserStatus` | Credential sai cùng 401; pending/inactive/locked 403; không lộ secret |
| AC-05 | Thay user status, active role và permission trực tiếp trong PostgreSQL giữa hai requests | Request kế tiếp dùng trạng thái/quyền mới; role inactive không cấp quyền; không có authority cache |
| AC-06 | Hai refresh đồng thời cùng token trên Redis thực; fault injection trước/sau atomic commit | Tối đa một rotation thành công, không có hai successor; Redis lỗi không fail open |
| AC-07 | Gửi lại spent token khi session ACTIVE, response-loss retry, expired/unknown/bad-signature token | Reused thu hồi đúng session; retry có thể thu hồi; các trường hợp khác không bị gán nhầm replay |
| AC-08 | Login hai session, logout một, dùng lại access/refresh JWT **chưa hết hạn** của từng session | Session logout nhận `AUTH_007` 401; session kia còn hiệu lực; không JTI blacklist |
| AC-09 | Mixed-case/prefixed role name, null/blank/invalid name, role collision, inactive role, permission code case-sensitive và code bắt đầu `ROLE_` | Đúng một `ROLE_`, không gộp collision hoặc cấp permission của role lỗi; permission hợp lệ giữ nguyên `code`, code invalid/prefix `ROLE_` không phát authority |
| AC-09 | User chỉ được gán permission `ROLE_ADMIN`, không có role ADMIN hợp lệ; gọi endpoint yêu cầu `hasRole('ADMIN')` và `hasAuthority('ROLE_ADMIN')` | Cả hai bị từ chối `AUTH_002` 403; permission không được đổi tên, gộp hoặc nâng thành role |
| AC-10 | HTTP security test cho 400/401/403/503 và body envelope ở controller + filter | Đúng `ApiResponse.error(...)`, `FieldErrorDetail` khi validation, `AUTH_001`/`AUTH_002` không đổi nghĩa |
| AC-11 | Method/path security test và production profile Swagger/OpenAPI test | Chỉ hai public routes; production docs không public; Logout cần access JWT |
| AC-12 | Config negative test thiếu/yếu secret; Redis key/TTL inspection | Startup fail khi key không hợp lệ; không hardcode secret/token trong keys hoặc logs |
| AC-13 | Testcontainers PostgreSQL + Redis hoặc dịch vụ cô lập tương đương | Kiểm chứng integration/atomicity pass; thiếu môi trường thật là verification failure |
| AC-14 | Production release-gate evidence cho chống brute-force/login abuse | Có biện pháp và verification trước production; không thêm rate limiter vào FEATURE-007 |
| AC-15 | Load/performance test cho đường xác thực protected requests | Báo cáo throughput/latency/query count Redis + PostgreSQL; không bỏ kiểm tra bắt buộc |
| AC-16 | Tạo access JWT và refresh JWT vừa hết hạn vừa có revoked tombstone; đối chứng với JWT chưa hết hạn cùng `sid` | Expired access → `AUTH_001`, expired refresh → `AUTH_005`; chỉ token còn hiệu lực thời gian → `AUTH_007` |

## 9. Decisions, Risks and Review Items

### Product Owner Decisions — Closed

- **B1/B2/B4; W1/W2/W4–W6; S1**: JWT HS512 với TTL đã chốt; Redis `sid` trên mọi protected request; quyền/status hiện hành từ PostgreSQL; email 320; repository query khác schema mutation; scope ba endpoints; Redis fail closed; production Swagger không public; security test matrix đầy đủ.
- **B3**: Strict atomic single-use rotation, không Idempotency-Key/cache/grace period. Spent-token replay thu hồi session liên quan; retry hợp lệ bằng token cũ có thể gây thu hồi. Mất response mà không có token mới thì login lại.
- **B5**: Giữ nghĩa `CommonErrorCode.AUTH_001`/`AUTH_002` và `ApiResponse.error(...)`; chỉ thêm mã cần phân biệt cho credential, refresh invalid/expired/reused, revoked session, inactive account và auth outage. Không dùng 409 mặc định.
- **W3/N2**: Role authority từ `RoleEntity.roleName`, trim/uppercase `Locale.ROOT`, thêm tối đa một `ROLE_`; permission authority hợp lệ giữ nguyên `PermissionEntity.code`; permission code bắt đầu `ROLE_` không phát thành authority; inactive/invalid/colliding roles không được cấp quyền.
- **OD-14**: JWT `iss=aquasuite-be`, access `aud=aquasuite-api`, refresh `aud=aquasuite-auth`; Spring Security OAuth2 Resource Server + Nimbus; secret từ cấu hình bảo mật.
- **OD-15**: Rate limiting trong ứng dụng ngoài scope FEATURE-007; chống brute-force là production prerequisite; Redis/PostgreSQL được đọc trên mỗi protected request, không authority cache.
- **N1**: JWT hết hạn được phân loại trước Redis revocation; `AUTH_007` chỉ dành cho token còn hiệu lực thời gian thuộc revoked session.

### Technical Review Items

1. CODEX_REVIEW cần xác minh atomic Redis script/CAS, spent marker/tombstone TTL, Redis cluster key placement và failure behavior khi mất kết nối trước/sau commit. Không thay đổi chính sách strict rotation.
2. Kiểm tra `JwtDecoder`/validators và `JwtAuthenticationConverter`/session integration của Resource Server: HS512, issuer/audience/token type, 401/403/503 mapping và không tự viết JWT filter khi không cần.
3. Inventory dữ liệu role/permission ở các môi trường cần bảo toàn trước deployment. Flyway V1 không chặn role name có whitespace/ký tự invalid hoặc collision sau normalization; không sửa schema/entity để che vấn đề.
4. Xác minh production login-abuse protection và đo tải Redis/PostgreSQL. Chưa có biện pháp chống brute-force thì production release bị chặn.
5. Xác minh exact dependency/config/files trong implementation plan sau Spec Review; tài liệu này không cấp quyền sửa chúng.

### Open Product Decisions

Không còn quyết định Product Owner mở trong phạm vi FEATURE-007 theo chỉ đạo hiện tại. Các mục Technical Review ở trên là bằng chứng và chi tiết triển khai cần CODEX_REVIEW kiểm tra, không mở lại quyết định PO.

## 10. Review and Next Action

Bản `DRAFT_FOR_REVIEW` này không phải Implementation Plan và không cấp quyền sửa source, tests, dependencies hoặc migration. Product Owner/Lead Architect và CODEX_REVIEW cần đánh giá độc lập các contract/AC, rủi ro và Technical Review Items trước khi có bất kỳ `SPEC_REVIEW: APPROVED` nào.
