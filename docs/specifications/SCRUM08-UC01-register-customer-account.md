# SCRUM08 - Register Customer Account (UC-01)

SPEC_ID: SCRUM08-UC01
SPEC_VERSION: 0.4.0
SPEC_STATUS: UPDATED_BY_USER_SCOPE
SPEC_REVIEW: APPROVED
IMPLEMENTATION_AUTHORIZED: NO

## Metadata and sources

- **Feature / scope**: FE-01 / SCRUM08 / UC-01; đăng ký Customer account, validation, OTP email và tạo hồ sơ customers liên kết user_id UUID.
- **Module / package**: `iam` sở hữu authentication, `crm` sở hữu customers; packages `com.aquasuite.ims.module.iam` và `com.aquasuite.ims.module.crm`, backend tại `D:\Workspace\aquasuite-be`.
- **Baseline**: Java 21, Spring Boot 3.5.16, PostgreSQL; User UUID, BCrypt, JWT và Redis session đã có.
- **Owner**: Project Owner / IAM team; risk HIGH vì xử lý credential và kích hoạt tài khoản.
- **Nguồn**: SRS `D:\AQS_Report-3_Software Requirement Specification_v1.0_29092026.pdf`, UC-01 trang PDF 33-35; `D:\Database.pdf` trang 1-3; source hiện tại.
- **Quy chuẩn**: [Architecture](../architecture/Architecture-Aquasuite.md), [HTTP conventions](../contracts/api-conventions.md), [module contracts](../architecture/module-contracts.md), [workflow](../workflow/feature-delivery.md), [template](../templates/feature-spec.md).
- **Human decisions**: Giữ UUID, không Google; email OTP trước/SMS sau; giữ implementation gọn và OTP activation; sau đó xác nhận “Có, tạo cả hồ sơ customers”.
- **Evidence**: [Customer profile confirmation](../../.agent/reviews/SCRUM08-UC01-customer-profile-confirmation.md); scope 0.3.0 là lịch sử trước bổ sung customers. Approval scope không giả lập technical Plan Review.

## 1. Phạm vi hiện tại

Luồng: nhập thông tin hợp lệ -> tạo tài khoản CUSTOMER PENDING_ACTIVATION -> gửi OTP email -> OTP đúng tạo hồ sơ customers và chuyển tài khoản ACTIVE trong cùng transaction -> dùng lại JWT/session login hiện có.

Tập trung: validation, kiểm trùng email/phone, BCrypt password, OTP email 6 số/5 phút, resend sau 60 giây, kích hoạt một lần và response an toàn.

Hoãn khỏi phiên bản này: activity audit mới, bảng/version điều khoản, Google, SMS, rate-limit theo IP/contact diện rộng, scheduler, cleanup job và hạ tầng cấu hình riêng. CRM chỉ triển khai hồ sơ customers tối thiểu cho đăng ký; chưa mở rộng dự án/sổ địa chỉ/chăm sóc khách hàng. Checkbox điều khoản vẫn được validation là true.

Revision 0.4.0 theo xác nhận trực tiếp “Có, tạo cả hồ sơ customers”, thay phần hoãn CRM của 0.3.0. Chưa coi là hoàn tất toàn UC-01 trong SRS: audit vẫn hoãn; User chưa xác minh tồn tại ở PENDING_ACTIVATION và không được login. Customer profile chỉ tồn tại sau activation thành công.

## 2. Hiện trạng và những phần tái sử dụng

| Source dưới module/iam | Hiện trạng / cách dùng |
|---|---|
| `internal/user/entity/User.java` | UUID, username, email, passwordHash, phone, status và fullName từ V6; User là identity dùng chung, role CUSTOMER cố định cho luồng này |
| `internal/user/repository/UserRepository.java` | Đã có finder email/phone và entity graph; thêm kiểm trùng phone và query khóa user khi activation/resend |
| `internal/user/enums/UserStatus.java` | Đã có PENDING_ACTIVATION/ACTIVE; không thêm enum trạng thái |
| `internal/authorization/repository/RoleRepository.java` | Tra CUSTOMER hiện hữu; chỉ gán role đang active |
| `internal/auth/security/AuthSecurityConfig.java` | PasswordEncoder BCrypt 12 và bảo vệ các API ngoài login/refresh; bổ sung đúng ba POST đăng ký |
| `internal/auth/service/impl/AuthServiceImpl.java` | Đã có cấp JWT/session. Tái sử dụng bằng helper nội bộ trong chính class này, không thêm AuthSessionIssuer |
| `internal/auth/token/JwtTokenService.java` | Giữ token format/claims/TTL/signing/validation; không tạo JWT verifier hoặc loại JWT mới |
| `internal/auth/token/RedisSessionStore.java` | Giữ nguyên V2 session, rotation và logout |
| `share/config/redis/RedisCacheHelper.java`, StringRedisTemplate | Redis đã có TTL; dùng namespace OTP registration riêng, không cần job xóa |
| `share/config/mail/MailConfig.java` | Tái sử dụng JavaMailSender. Gửi nội dung OTP đăng ký 5 phút; không gọi sender recovery 10 phút/nuốt lỗi |

## 3. Actor và token

Guest được register, verify và resend mà không cần JWT. Sau kích hoạt, Customer dùng access/refresh JWT như login hiện tại. Client không chọn role/status/userId.

Không dùng access/refresh JWT để chứng minh đã xác minh email. OTP đăng ký chứng minh quyền truy cập email; JWT login chứng minh phiên đăng nhập sau khi User ACTIVE. Không có registrationToken, challenge token, HMAC key hay secret cấu hình mới.

Verify thành công gọi method nội bộ IAM lấy User vừa kích hoạt và tạo session bằng JwtTokenService + RedisSessionStore hiện có. Method này không có HTTP endpoint và không nhận userId từ client làm căn cứ cấp token. Không lưu password rõ để gọi login lại.

Không log password/hash, OTP, Redis payload, JWT hoặc request chứa credential. Không trả JPA Entity. Response dùng DTO và ApiResponse hiện tại.

## 4. Validation và quy tắc nghiệp vụ

| Input | Rule |
|---|---|
| fullName | Bắt buộc, trim, 2-50 ký tự, hỗ trợ tiếng Việt |
| email | Bắt buộc, trim, đúng định dạng, <=320 ký tự; unique ignore-case |
| phone | Bắt buộc, trim, đúng `0[35789][0-9]{8}`; unique |
| password | >=8 ký tự, có hoa/thường/số/ký tự đặc biệt; <=72 bytes UTF-8; không trim/normalize |
| passwordConfirmation | Bằng password chính xác, không persist |
| termsAccepted | Bắt buộc true; chỉ validation trong scope này |

1. Trùng email hoặc phone, kể cả User PENDING_ACTIVATION/INACTIVE/LOCKED, trả 409 REG_001. Không overwrite credential của tài khoản pending; người dùng pending dùng resend/verify.
2. Server sinh UUID và username `customer-<UUID>`; fullName không dùng làm unique username.
3. Chỉ gán CUSTOMER đang active. Thiếu/inactive role -> REG_008; không tự tạo role hoặc fallback STAFF/ADMIN.
4. Register lưu passwordHash và PENDING_ACTIVATION trong transaction; chưa cấp JWT. Email provider lỗi không làm User thành ACTIVE; có thể resend.
5. OTP sinh SecureRandom 6 số, lưu hash bằng PasswordEncoder hiện có trong Redis, TTL 300 giây. Dùng key gắn user UUID, tách namespace recovery/auth session; không lưu OTP rõ.
6. Redis giữ timestamp gửi gần nhất, OTP hash và attempts cùng TTL. Resend trước 60 giây bị chặn; resend thay mã cũ. Tối đa 5 lần sai trong một chu kỳ OTP; resend không đặt lại attempts khi chu kỳ hiện tại chưa hết hạn.
7. OTP hết TTL không được kích hoạt. Redis tự xóa OTP; không có bảng challenge hoặc cleanup scheduler.
8. Verify/resend serialize theo User bằng khóa DB; verify chỉ nhận User PENDING_ACTIVATION. Kiểm hash/TTL/attempts; consume OTP atomic theo phiên bản hiện hành trước khi commit activation. Replay không cấp thêm token.
9. Nếu consume OTP thành công nhưng DB rollback: User vẫn pending, yêu cầu resend. Không xây distributed transaction hoặc saga cho bước này.
10. Nếu User đã ACTIVE nhưng tạo Redis session thất bại: giữ ACTIVE và trả REG_009, hướng dẫn dùng API login hiện có. Không xóa User, không trả token một phần.
11. User pending không được tự xóa bằng background job trong scope hiện tại. Đây là giới hạn có chủ đích khi bỏ cleanup.

## 5. Data model và migration tối thiểu

- Giữ User UUID, các bảng roles/user_roles và mapping hiện hữu.
- Thêm `users.full_name VARCHAR(50) NULL` và field User.fullName; nullable cho dữ liệu cũ, register mới bắt buộc fullName.
- Thêm unique index phone non-null; email/username đã có unique lower index từ V4.
- V6__SCRUM08_user_registration.sql đã triển khai ở revision trước; giữ nguyên V1-V6, thêm V7 cho hồ sơ customers.
- Preflight phone trùng/noncanonical: báo lỗi đã che PII, không tự merge/delete/đổi số của User cũ.
- V7 thêm customers theo Database.pdf: customer_id UUID PK, user_id UUID NOT NULL UNIQUE FK users(id) ON DELETE RESTRICT, customer_code VARCHAR(50) UNIQUE NOT NULL, customer_name VARCHAR(50), phone VARCHAR(32), email VARCHAR(320), customer_type VARCHAR(32), address TEXT NULL, company_name VARCHAR(255) NULL, status VARCHAR(32), created_at/updated_at TIMESTAMPTZ. Required fields ngoài address/company đều NOT NULL. Mặc định RETAIL/ACTIVE, code CUS-UUID. Không backfill/phỏng đoán profile của User cũ.
- CRM sở hữu customers; user_id là UUID scalar, không JPA relationship chéo feature. Không thêm registration_challenges, user_terms_acceptances, outbox hoặc bảng session mới. Không sửa user_password_recovery.
- Các write tạo User + CUSTOMER assignment là một transaction; activation là một transaction khác. DB unique constraints xử lý race, không chỉ exists check ở Java.

## 6. HTTP/API contract

| Method / path | Body | Success |
|---|---|---|
| POST `/api/v1/customer-registrations` | fullName, email, phone, password, passwordConfirmation, termsAccepted | 201 ApiResponse: id UUID, email, status=PENDING_ACTIVATION; không token |
| POST `/api/v1/customer-registrations/verifications` | email, otp (string 6 số) | 200 ApiResponse<LoginResponse>: User ACTIVE, access_token/refresh_token/token_type/expires_in/user theo login hiện tại |
| POST `/api/v1/customer-registrations/otp-resends` | email | 200 ApiResponse thông báo đã gửi OTP email |

Chỉ ba POST này được thêm vào public allowlist. Không mở wildcard, không thay security của catalog/inventory/password. Header token responses `Cache-Control: no-store`. OTP được tìm bằng email -> User UUID trên server; không cần registrationToken. Contract cũ 0.2.0 về challenge token/201 verify đã superseded, chưa có source consumer triển khai.

| HTTP | Code | Condition |
|---|---|---|
| 400 | SYS_003 | Validation field bắt buộc, định dạng, password policy/confirmation, terms |
| 400 | SYS_002 | JSON/binding sai theo GlobalExceptionHandler hiện có |
| 409 | REG_001 | Email/phone trùng, gồm unique race; thông điệp chung không chỉ rõ account |
| 400 | REG_002 | OTP sai; trả attempts còn lại trong message |
| 410 | REG_003 | OTP không tồn tại/hết hạn; yêu cầu resend |
| 429 | REG_004 | Chưa đủ 60 giây hoặc hết attempts |
| 409 | REG_006 | User không còn pending; không kích hoạt/cấp session lại |
| 503 | REG_007 | Email send lỗi; không báo đã gửi thành công |
| 503 | REG_008 | Registration dependency/role không khả dụng; không activate fail-open |
| 503 | REG_009 | Activation đã commit nhưng auto-login lỗi; login lại |
| 409 | DB_001 | Integrity error khác không phải known email/phone constraint |

Không công bố lại REG_005/010/011 khi không còn operation tương ứng; không tái sử dụng chúng cho nghĩa khác. Không có field channel/SMS endpoint trong scope email-only.

IAM dùng `AuthService.loginRegisteredUser(UUID userId)` sau OTP; reload ACTIVE user/roles, dùng chung helper cấp session với login.

CRM public contract: `crm/api/CustomerRegistrationApi.createProfile(CustomerRegistrationRequest)`; caller IAM, provider CRM. Input DTO non-null gồm userId UUID, fullName 2-50, phone/email theo validation form; output DTO customerId UUID/customerCode, không Entity. Chỉ nội bộ sau OTP, propagation MANDATORY tham gia transaction activation. Cùng userId/cùng dữ liệu trả lại profile hiện có; dữ liệu khác DB_001 conflict, input invalid SYS_003. IAM map lỗi tạo profile thành REG_008 và rollback activation/profile. API additive, không domain event hoặc import CRM internal từ IAM.

## 7. Acceptance criteria và verification

| AC | Tiêu chí / test cần có |
|---|---|
| AC-01 | Validation đúng boundary tên/email/phone/password/confirmation/terms; malformed JSON trả 400; không lưu password rõ |
| AC-02 | Register tạo đúng một UUID User PENDING_ACTIVATION + CUSTOMER, BCrypt hash và fullName; chưa login/cấp JWT được |
| AC-03 | Trùng email ignore-case/phone và race request trả 409, không overwrite pending User hoặc tạo orphan role assignment |
| AC-04 | OTP email 6 số/TTL300; 299 giây hợp lệ, tại 300 hết hạn; resend 59 giây bị chặn, 60 cho phép; code cũ vô hiệu |
| AC-05 | OTP sai ghi attempts thực sự, 5 lần bị chặn; verify/resend song song không kích hoạt bằng code cũ hoặc cấp session hai lần |
| AC-06 | OTP đúng -> ACTIVE; JWT/session tạo qua auth hiện có và gọi protected endpoint được; pending/inactive/locked không được cấp token |
| AC-07 | Email/Redis/DB failure không activate sai; OTP consumed + DB rollback -> resend; activation commit + session fail -> login recovery |
| AC-08 | Public chỉ ba POST, client không nâng role/status, không lộ OTP/hash/token qua log; login/refresh/logout/role boundaries hồi quy pass |
| AC-09 | Fresh/upgrade V6/V7 giữ dữ liệu cũ/V1-V6 bytes, không backfill profile User cũ; không audit/challenge/config/job/token subsystem |
| AC-10 | OTP đúng tạo đúng một customers với user_id UUID, customer_name/fullName, phone/email từ form, RETAIL/ACTIVE, code unique; không có profile khi pending, OTP sai/hết hạn hoặc activation rollback |
| AC-11 | Lỗi CRM rollback activation và profile; resend/retry tạo thành công; cùng userId/payload idempotent, payload khác conflict; không import internal/JPA link chéo module |

Integration dùng PostgreSQL và Redis thật hoặc Testcontainers hiện có; mail test adapter không gửi thật. Kết quả implementation và verification hiện tại nằm trong handoff SCRUM08-UC01; không dùng số test của revision cũ làm evidence cho bổ sung customers.

## 8. Revision history và trạng thái

| Version | Change |
|---|---|
| 0.1.0 | Draft đầy đủ từ SRS/database/source |
| 0.2.0 | User duyệt draft, chốt email trước/SMS sau |
| 0.3.0 | User yêu cầu đơn giản: chỉ User + validation, vẫn OTP email activation; Redis TTL, dùng lại login JWT; hoãn CRM/audit/terms persistence và bỏ cleanup/config riêng |
| 0.4.0 | User xác nhận cần cả hồ sơ customers; thêm CRM tối thiểu qua public API và transaction chung với activation; giữ phần OTP/JWT gọn |

[Plan hiện tại](../../.agent/plans/active/SCRUM08-UC01.md) revision 4 bao gồm customer profile. Independent review vẫn PENDING; user trực tiếp yêu cầu triển khai scope này, không tự gán approval kỹ thuật. Bảo toàn dirty changes, historical approval evidence và archived artifacts; không stage/commit.
