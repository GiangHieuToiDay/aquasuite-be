# SCRUM08 / UC-01 - Simplified Implementation Plan

PLAN_ID: SCRUM08-UC01
PLAN_VERSION: 4
PLAN_STATUS: READY_FOR_REVIEW
PLAN_REVIEW: PENDING
IMPLEMENTATION_STATUS: NOT_STARTED
IMPLEMENTATION_AUTHORIZED: NO
SPEC_ID: SCRUM08-UC01
SPEC_VERSION: 0.4.0
SPEC_REVIEW: APPROVED
SPEC_SHA256: b47fa98475044b6750be5d91bb8dcece934f15f4d252588abe4954c9f131aafc
SCOPE: BACKEND_ONLY_CUSTOMER_ACCOUNT_AND_PROFILE
DATE: 2026-10-20

## 1. User-directed scope change

User yêu cầu tập trung đăng ký User và validation, bỏ các lớp cấu hình/job phức tạp, dùng lại token login; sau đó xác nhận “Vẫn giữ OTP email để kích hoạt”. Xem [spec 0.3.0](../../../docs/specifications/SCRUM08-UC01-register-customer-account.md) và [scope evidence](../../reviews/SCRUM08-UC01-scope-change-v0.3.0.md).

Plan v2 thay v1; v1 chưa triển khai. Không tạo RegistrationCleanupJob, RegistrationProperties, RegistrationConfig, RegistrationTimeSource, RegistrationCryptoService, RegistrationQuotaService, AuthSessionIssuer hoặc hạ tầng challenge/token riêng. CRM triển khai profile tối thiểu; hoãn audit, terms persistence, Google/SMS và rate-limit diện rộng. Redis TTL làm OTP hết hạn, không cần scheduler.

Luồng cuối: validate -> CUSTOMER account PENDING_ACTIVATION -> email OTP -> tạo customers và ACTIVE cùng transaction -> JWT/session login hiện có. Không coi JWT login là token xác minh email.

## 2. Baseline và governance

- Repo duy nhất: BE tại `D:\Workspace\aquasuite-be`, HEAD `233916b` cộng dirty baseline hiện tại; không thao tác sibling FE hoặc vị trí cũ D:\DO_AN.
- Java 21, Boot 3.5.16, package com.aquasuite.ims; Flyway hiện cao nhất V5. Giữ V1-V5 và auth V2 JWT/Redis.
- Đã đọc docs README, architecture, HTTP/module contracts và feature-delivery trong phiên làm việc. User trực tiếp yêu cầu scope change nên spec/plan mới supersede các lựa chọn phức tạp trước đó.
- Bảo toàn dirty catalog/inventory, plan/handoff SCRUM14 và mọi archive. Appendix hash v1 được giữ cho các file source chưa sửa; bổ sung baseline file mới vào scope trước review.
- Không sửa protected IAM skeleton UserController/UserService/UserServiceImpl/UserRequest/UserResponse/UserExternalService: persistence tests hiện kiểm hash của chúng. Dùng RegisterController/RegisterService riêng, không cần phá nền tảng cũ.
- [Workflow](../../../docs/workflow/feature-delivery.md) yêu cầu canonical `.agent/reviews/plan-review.md` đúng PLAN_ID/version/SHA-256 với PLAN_REVIEW APPROVED trước code; snapshot PENDING ở đây không cần đổi sau approval. Không tự cấp independent approval.

## 3. Các bước thực hiện tuần tự

| Step | Công việc | Exit evidence |
|---|---|---|
| 1 | Chụp status/hash; xác minh Plan Review v2 và spec hash; kiểm migration highest V5; chuẩn bị PostgreSQL/Redis test cô lập | Không có drift/conflict ngoài scope; baseline tests có evidence |
| 2 | V6 thêm users.full_name nullable và unique phone non-null; map fullName, phone exists và user lock query | Fresh/upgrade migration + duplicate preflight rollback pass; V1-V5 unchanged |
| 3 | DTO/Bean Validation cho form, password complexity/bytes/confirmation/terms; service check duplicate và CUSTOMER active | Invalid không ghi User; duplicate không overwrite account pending |
| 4 | RegisterService dùng TransactionTemplate hiện có: tạo UUID/username server-generated, BCrypt, PENDING_ACTIVATION + CUSTOMER; commit rồi lưu OTP hash/TTL300 và gửi email qua JavaMailSender hiện có | Pending User không login; email lỗi báo rõ, có thể resend |
| 5 | Verify/resend khóa user pending trong transaction; Redis OTP state gồm hash, attempts, sentAt, version; SecureRandom 6 số, BCrypt OTP, cooldown60, max5 wrong attempts; Redis atomic consume đúng version | TTL boundary/old-code/replay/race đúng; không có cleanup/config mới |
| 6 | AuthService thêm method nội bộ loginRegisteredUser(UUID); AuthServiceImpl dùng shared private helper với login hiện có để cấp session sau activation commit | Cùng JWT/session implementation; không thêm token service/JWT type/verifier |
| 7 | Ba endpoint và exact public POST allowlist; DTO/ApiResponse, error enum, no-store khi trả token | Không mở wildcard; không client-specified role/status; auth regression pass |
| 8 | Targeted tests -> full clean verify -> code guide/handoff | AC01-09 có evidence; code review/Final Gate chưa tự nhận pass |

### Transaction và lỗi cần xử lý, không thêm hạ tầng

- Tạo User và role assignment cùng DB transaction. Register trùng pending trả REG_001, không thay password/phone/fullName của pending account.
- RegisterService orchestration không giữ DB transaction cho toàn HTTP request: dùng TransactionTemplate để transaction kết thúc trước gửi mail hoặc cấp JWT. Không dùng self-invocation @Transactional rồi giả định commit đã xảy ra.
- OTP lưu riêng trong Redis namespace registration, không dùng user_password_recovery hoặc sửa legacy recovery sender. JavaMailSender đã có bean; gửi nội dung đăng ký 5 phút trong private method của RegisterService, không thêm một hệ sender/interface/config khác.
- Verify/resend serialize bằng PESSIMISTIC_WRITE query cùng User; wrong-attempt counter ở Redis nên không bị DB rollback làm mất. Resend không reset attempts còn hiệu lực. Redis TTL tự loại OTP đã hết hạn.
- Khi so mã: dùng BCrypt matches. Khi consume: atomic compare version/value-and-delete qua StringRedisTemplate; không dùng GET rồi DELETE đơn thuần có thể xóa code vừa resend. RedisCacheHelper chỉ dùng các thao tác TTL hiện có nếu phù hợp, không mở rộng shared helper cho business logic.
- Consume OTP trước commit activation: nếu DB rollback thì User vẫn pending, cần resend. Nếu activation commit xong mà session lỗi thì giữ ACTIVE, REG_009 và dùng login hiện có. Không saga/outbox/distributed transaction.
- Email send bên ngoài transaction/lock. SMTP lỗi/timeout không báo thành công hoặc xóa account đã commit; User pending chỉ resend/verify và không được login.
- Missing CUSTOMER/inactive role fail closed REG_008. Không auto-seed role/credential hoặc fallback quyền.
- User pending được giữ lại, không cleanup ở phiên bản này. Redis unavailable không được bỏ qua OTP. Không thêm HMAC secret hoặc business properties; OTP TTL/cooldown/attempt constants đặt tại service.

## 4. Exact production file scope

Prefix J = `src/main/java/com/aquasuite/ims/module/iam/`. N=new, M=modify. Các file ngoài danh sách được bảo toàn; cần thêm file thì revise plan/version trước code.

| Type | Path under J unless stated | Purpose |
|---|---|---|
| N | `src/main/resources/db/migration/V6__SCRUM08_user_registration.sql` (repo root) | full_name và unique phone/preflight; không challenge/CRM/terms tables |
| M | `internal/user/entity/User.java` | fullName nullable cho legacy; không đổi UUID/status/roles mapping |
| M | `internal/user/repository/UserRepository.java` | exists phone, lock query phục vụ verify/resend; giữ finder/entity graph cũ |
| N | `internal/registration/controller/RegisterController.java` | Ba POST và no-store token response |
| N | `internal/registration/service/RegisterService.java` | Register/OTP/resend/activation, nested OTP state DTO, private mail method; không thêm service interface |
| N | `internal/registration/payload/RegisterRequest.java` | Form validation, không toString password |
| N | `internal/registration/payload/RegisterResponse.java` | UUID/email/status, không entity/secret |
| N | `internal/registration/payload/VerifyRegistrationRequest.java` | Email/OTP validation |
| N | `internal/registration/payload/ResendRegistrationOtpRequest.java` | Email only; không channel/registrationToken |
| N | `internal/registration/validation/ValidRegistration.java` | Class-level confirmation/password-byte constraint |
| N | `internal/registration/validation/RegistrationValidator.java` | Field-specific password/confirmation errors |
| N | `internal/registration/error/RegistrationErrorCode.java` | REG codes đúng spec, implements BaseErrorCode |
| M | `internal/auth/security/AuthSecurityConfig.java` | PermitAll đúng ba POST registration; không đổi JWT/filter behavior |
| M | `internal/auth/service/AuthService.java` | Method nội bộ loginRegisteredUser(UUID) sau verify |
| M | `internal/auth/service/impl/AuthServiceImpl.java` | Shared private session helper cho login và registration; không đổi HTTP contract |

AuthService.loginRegisteredUser chỉ được gọi từ RegisterService sau OTP/DB commit; reload current ACTIVE User/roles, không expose endpoint nhận UUID để cấp token. Password login vẫn xác thực như cũ trước shared helper. Không sửa JwtTokenService, RedisSessionStore, Clock/catalog config, recovery, application profiles, shared config, dependencies hoặc protected skeleton files.

## 5. Test và docs scope

Prefix T = `src/test/java/com/aquasuite/ims/`. Dùng AuthIntegrationTestSupport/PostgreSqlIntegrationTestSupport hiện có; test SMTP bằng mock JavaMailSender/capture, không gửi thư thật.

| Type | Path under T unless stated | Coverage |
|---|---|---|
| N | `module/iam/internal/registration/RegisterValidationTest.java` | Name/email/phone/password UTF-8/confirmation/terms boundaries |
| N | `module/iam/internal/registration/RegisterApiIntegrationTest.java` | Register pending, BCrypt, duplicate, verify ACTIVE, token usable, wrong methods/privilege injection |
| N | `module/iam/internal/registration/RegisterOtpIntegrationTest.java` | TTL299/300, resend59/60, leading zero, wrong attempts, mail/Redis failures, login recovery |
| N | `module/iam/internal/registration/RegisterConcurrencyIT.java` | Duplicate race, verify/resend, verify replay, consume/DB rollback |
| N | `module/iam/internal/registration/RegisterMigrationIT.java` | Fresh/upgrade V6, fullName legacy nullable, unique phone/preflight rollback |
| M | `module/iam/internal/auth/AuthApiIntegrationTest.java` | Shared issuance helper regression, pending not login |
| M | `module/iam/internal/auth/AuthProductionSecurityTest.java` | Ba ngoại lệ public mới, paths còn lại protected |
| M | `module/iam/internal/persistence/IamMigrationIntegrationTest.java` | Giữ strict schema assertions, bổ sung đúng full_name nullable do V6; không sửa skeleton hash assertions |
| M | `support/FlywayMigrationIT.java` | Cập nhật số migration latest từ 5 sang 6; giữ nguyên kiểm tra history, baseline và checksum |
| M | `integration/AuthenticationUpgradeIT.java` | Upgrade V3 đến latest chạy 3 migration (V4-V6); giữ mọi assertion bảo toàn dữ liệu |
| M | `docs/README.md` (repo root) | Spec/plan scope hiện tại |
| M | `docs/migrations/flyway.md` (repo root) | V6 additive và preflight |
| N | `docs/SCRUM08-UC01-code-guide.md` (repo root) | Luồng gọn, reused JWT/Redis/mail và recovery giới hạn |
| N | `.agent/handoffs/SCRUM08-UC01.md` (repo root) | Evidence sau implementation; không tạo handoff success lúc lập plan |

Không sửa test hash của foundation để hợp thức hóa việc đổi skeleton. Các test đang có tiếp tục chạy, chỉ sửa hai auth tests nêu trên để phản ánh allowlist/helper mới. Extra test/config edit cần scope revision.

## 6. Verification và gates

Sau Plan Review, chạy từ BE:

```powershell
./mvnw.cmd -DskipTests compile
./mvnw.cmd '-Dtest=RegisterValidationTest,RegisterApiIntegrationTest,RegisterOtpIntegrationTest,AuthApiIntegrationTest,AuthProductionSecurityTest' test
./mvnw.cmd clean -Pphase1-it verify
git diff --check
Get-FileHash -Algorithm SHA256 .agent/plans/active/SCRUM08-UC01.md
```

Full verify bao gồm các IT mới, foundation hash tests và authentication/catalog/inventory regression. Kiểm XML Surefire/Failsafe thực tế; mock-only không chứng minh race/DB/Redis. Hạ tầng thiếu -> NOT_RUN, không báo PASS. Không chạy migration trên DB live hoặc dùng mail thật trong tests.

Bàn giao map AC01-09 của spec tới kết quả, ghi commands/timestamp/counts/failures/skips, files changed, contract verification, deviations và known limitations. Không mượn số 251 tests lịch sử. Chỉ sau handoff mới Code Review; Final Gate theo workflow. Không stage/commit/push/deploy.

## 7. Revision và baseline

- v3: bổ sung compatibility assertions trong IamMigrationIntegrationTest (full_name nullable, phone unique nhưng cho phép nhiều NULL), FlywayMigrationIT (6 migrations) và AuthenticationUpgradeIT (V3 -> latest chạy V4-V6). Giữ mọi assertion bảo toàn dữ liệu và protected skeletons. Repository dùng atomic INSERT ON CONFLICT DO NOTHING để xử lý duplicate race mà không log SQL chứa password hash. Không đổi HTTP/spec hoặc thêm schema ngoài V6. User trực tiếp yêu cầu triển khai; independent review vẫn pending. Hash v2 trước revision: 0638425deaa5f05fe2ee1780fb41b6da26e1a2349a2533456a900f8de26fd41d. Trạng thái metadata đầu plan là snapshot trước thực thi; kết quả hiện tại nằm trong handoff SCRUM08-UC01.

- v1: spec 0.2.0, OTP challenge/CRM/audit/cleanup/config đầy đủ; chưa triển khai. SHA-256 trước supersede: `4d8fb7d9e71ed1aeb3b319694c69f7bd88ff5406030724249fa9876fa47c7f14`.
- v2: đơn giản theo chỉ đạo User; giữ OTP email kích hoạt, chỉ User+validation và reuse JWT; bỏ các lớp/hạ tầng nêu tại mục 1.
- Historical approval spec 0.2.0 giữ nguyên; scope evidence mới ghi rõ không dùng hash cũ cho revision mới. Plan Review v2 PENDING, implementation NOT_STARTED.
- Bảng hash dưới từ baseline v1; chưa sửa source nên tiếp tục hợp lệ. User.java được chuyển vào planned modification cho fullName. Hash plan giữ ngoài chính plan để tránh tự tham chiếu.

| Baseline classification | Repository-relative path | SHA-256 |
|---|---|---|
| Dirty baseline: protected | `.agent/handoffs/SCRUM14-UC13-phase1-verification.md` | `c9534fec5450796f57da4a92bd33dc9beefd9510c6fd06000f5f681572cc25b1` |
| Dirty baseline: protected | `.agent/handoffs/SCRUM14-UC13-phase2-verification.md` | `d60a063862abe5e1e9c84d121107a1a90934adb21ed7f120a793db67da8d52f3` |
| Dirty baseline: protected | `.agent/handoffs/authentication-merge-verification.md` | `31e052461cd8b3a9b36b84877dbd018334759631853887145a2d49662142785f` |
| Dirty baseline: protected | `.agent/plans/SCRUM14-UC13.md` | `33c490cb39fdaa72214a36c6cc270c7c6b1328320d7d43293916e49a22b30fc4` |
| Protected: unchanged | `AGENTS.md` | `d6888b4a2712f60ccd9cc087b41b0b8e47cdbbfc85e8a71b57256f3c9ecb26c4` |
| Dirty baseline: protected | `docs/SCRUM14-UC13-phase1-code-guide.md` | `b94793ffdfe626b30613b2a4ced8bdba54268ac682f7c94eb5c3a9f70eb0b7e4` |
| Protected: unchanged | `pom.xml` | `7cf60417bc4d970a8d88b29f021ff91565d93780dd4f7c64d1e94ae2f07d7db0` |
| Protected: unchanged | `src/main/java/com/aquasuite/ims/module/audit/api/AuditApi.java` | `ec619612899982036f60859bf1ad4752b2d4159befe10b7721623d0bca7cb9ec` |
| Protected: unchanged | `src/main/java/com/aquasuite/ims/module/audit/internal/service/AuditService.java` | `b1ea44e5491d2052db60138496e094eb4014fad9246edfeb4578c6d2c4b70f6b` |
| Protected: unchanged | `src/main/java/com/aquasuite/ims/module/catalog/internal/config/BundleClockConfig.java` | `fedfd71d1bcbef3338520f173fcf01fec715e55597be1a16714d2321e6314e45` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/controller/BundleAdminController.java` | `32ec3f5f481eb98b334a8fd47346d3e1ae93cbe392e8a436b15aec1aad1ff017` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/controller/BundleStorefrontController.java` | `b16cf1f24b6d6b116a387a9ad60821c2c6f126330b50e1be67042eb7db5e919d` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/entity/BundleComponentGroup.java` | `9aba32621a1cc50c03ace6e65bd3479e21599428e64c3a4a6e173612e3742021` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/entity/BundleConfiguration.java` | `9665ab5406ebbb0debd0e40e3049afc2be0d092a159c44fd3c743d42117978d2` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/mapper/BundleCoreMapper.java` | `d4dcec87c85e4e4b37e774fea70c9e59d5d4a7a575b5820f0ef8418a8185bb12` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleComponentGroupRequest.java` | `056ea61c13227b0b7e413de5d3abccea2c66ea92f56f2730abe05bd9428581f1` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreCreateRequest.java` | `0243c12c5d3cb2f49d485450e5076be77f2cea14f6a8908fd06816753faa7706` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleCoreUpdateRequest.java` | `f0aac08ab5acd0db3bfde3e79ebfce0b8db2e35ab506711420f9998987771e53` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleEvaluation.java` | `83e65e3174e0d3d0f1bf728f64f65fb8add5e88a1e8596b945bc6db0d847236a` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleEvaluationRequest.java` | `5fd6dd4a160e26336ab0f6c7e8d46f17b39b64b7579c11d5ed63f44c369155ba` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/payload/BundleVersionRequest.java` | `1b1ae45c395624d9b8e2e10506dce230e88d0940f460a86acde9a0a62e8fea11` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleAdminService.java` | `17a9b3971b6531d0296a825701b9212bc3372fd132ab63b463ceea06b5d1f4ae` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleStorefrontService.java` | `b6a8c81476fa14bdf79e0ce8237652445f7768379b8d0d3617b01feeeffac358` |
| Planned modification: starting bytes | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/security/AuthSecurityConfig.java` | `b73e7df8366cda271eaf99fae47ae679a1559c8967d1e20f8d12934afbc4fa66` |
| Planned modification: starting bytes | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/service/impl/AuthServiceImpl.java` | `f3ff227a6221475bc05de1f82344ea110ebff2d5b28b0f18e9651ccd8154d86b` |
| Protected: unchanged | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/token/JwtTokenService.java` | `d25e4cb90f7e820627e84f837ae824eb3a4c622efe9cbb6ed50b5c1e7bbfaf4b` |
| Protected: unchanged | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/token/RedisSessionStore.java` | `57c84f5b3023997ed5dcb2c0d6ac91df596c9b5b5f6bef622128c7d4a3334a62` |
| Protected: unchanged | `src/main/java/com/aquasuite/ims/module/iam/internal/service/otp/EmailOtpSender.java` | `910b257a1d7c21092e9bc1b48380594896ceae73ed00359fa71a7b3b8915aafe` |
| Protected: unchanged | `src/main/java/com/aquasuite/ims/module/iam/internal/service/otp/SmsOtpSender.java` | `e5f5e2bb688f603414f629fa88194c1775016e489303ba3015a4671209a72f23` |
| Planned modification: starting bytes | `src/main/java/com/aquasuite/ims/module/iam/internal/user/entity/User.java` | `043d94b0c1ab1ebe3260faa5e4474f5ed1b8ed6e8628a302c48437291fb603f9` |
| Planned modification: starting bytes | `src/main/java/com/aquasuite/ims/module/iam/internal/user/repository/UserRepository.java` | `3f625ab4214da02f41e35f04cc8740b2d3dd21b70edd54696be22c0a2e816622` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/inventory/api/InventoryAvailabilityApi.java` | `24998598b111b97d781764514765754e7948a6b6f2bcc4ae6e808d5280622da2` |
| Dirty baseline: protected | `src/main/java/com/aquasuite/ims/module/inventory/internal/service/impl/InventoryServiceImpl.java` | `f2dcc64623c0fa5b80e721cd4242b65b4e9c834ad2c56e9574a9b507c286cbe5` |
| Protected migration: unchanged | `src/main/resources/db/migration/V1__Initial_legacy_schema.sql` | `731f6997cac3ec1d10e9616c23ba58c0461c69e04458e7eebde980b20930aa1f` |
| Protected migration: unchanged | `src/main/resources/db/migration/V2__SCRUM14_phase1_combo_core.sql` | `71dcdcb9e426e31bb268670694db7f8b43a381f363338e59299e66cbb771a79b` |
| Protected migration: unchanged | `src/main/resources/db/migration/V3__SCRUM14_phase2_combo_options.sql` | `fa7eebc782b1663e1160d0ff7a45c76944913bc8b895b9c61aa8cdc68fe74ad8` |
| Protected migration: unchanged | `src/main/resources/db/migration/V4__integrate_uuid_authentication.sql` | `51ae7af697a15754977321f3e89db4d825ee71a0ebd07c067a4c2ed71db52630` |
| Protected migration: unchanged | `src/main/resources/db/migration/V5__preserve_password_recovery.sql` | `854c93f7554104b36be36952a42de6dd2b91f4224bef7b0b3060027cce85ca91` |
| Protected: unchanged | `src/main/resources/redis/revoke-session.lua` | `6b1f8d1e6f6e1c08ee60e7e4bfb0ab1c62455f82b998e997bc9e297351fd22dc` |
| Protected: unchanged | `src/main/resources/redis/rotate-refresh.lua` | `82ae24a43b276311ebffa041ae66cb99db46fc4e334fb9ee2c3991258bd7a099` |
| Planned modification: starting bytes | `src/main/java/com/aquasuite/ims/module/iam/internal/auth/service/AuthService.java` | `a439a83a1894f6e9cc6200ed6c7932bde9a7cc417c0d424fe9754b8018628328` |

## Customer profile amendment v4 (current scope)

User xác nhận “Có, tạo cả hồ sơ customers”. Phần hoãn CRM của v3 được thay thế bởi CRM tối thiểu; OTP/JWT vẫn dùng hạ tầng hiện có, không thêm config/job hay audit. Đây là chỉ đạo trực tiếp thực hiện phần bổ sung, không giả lập independent review.

- V7__SCRUM08_customer_profiles.sql tạo bảng customers theo Database.pdf trang 2: UUID PK, UUID user_id UNIQUE NOT NULL FK users ON DELETE RESTRICT; customer_code/name/phone/email/type/address/company/status/created_at/updated_at. Không backfill User cũ.
- CRM public API CustomerRegistrationApi.createProfile với DTO public; MANDATORY, userId scalar, không import internal hoặc entity chéo feature. Cùng userId/payload idempotent; khác payload conflict.
- RegisterService gọi API sau OTP consume, trước ACTIVE/commit. Profile và activation cùng DB transaction; CRM failure REG_008, rollback profile/activation, resend phục hồi. Session sau commit và HTTP LoginResponse giữ nguyên.
- Default profile RETAIL/ACTIVE, CUS-UUID; address/company nullable vì form không có dữ liệu này.
- Test fixture PostgreSqlIntegrationTestSupport xóa customers trước account fixture cho mọi DB integration test (AuthIntegrationTestSupport kế thừa) để giữ FK RESTRICT ở production; không đổi constraint chỉ để vượt qua tests.

### File scope bổ sung v4 (repository-relative)

| Type | Path | Purpose |
|---|---|---|
| N | src/main/resources/db/migration/V7__SCRUM08_customer_profiles.sql | Customer schema, FK/uniqueness/defaults |
| N | src/main/java/com/aquasuite/ims/module/crm/api/CustomerRegistrationApi.java | Public synchronous API |
| N | src/main/java/com/aquasuite/ims/module/crm/api/dto/CustomerRegistrationRequest.java | Validated scalar input |
| N | src/main/java/com/aquasuite/ims/module/crm/api/dto/CustomerRegistrationResponse.java | Safe result DTO |
| N | src/main/java/com/aquasuite/ims/module/crm/internal/entity/Customer.java | PDF customer profile, no cross-feature JPA reference |
| N | src/main/java/com/aquasuite/ims/module/crm/internal/repository/CustomerRepository.java | Find by user_id |
| N | src/main/java/com/aquasuite/ims/module/crm/internal/service/impl/CustomerRegistrationServiceImpl.java | Profile creation/idempotency in caller transaction |
| M | src/main/java/com/aquasuite/ims/module/iam/internal/registration/service/RegisterService.java | Call CRM API in activation transaction |
| M | src/test/java/com/aquasuite/ims/testsupport/PostgreSqlIntegrationTestSupport.java | Clean profile fixtures before account fixtures |
| N | src/test/java/com/aquasuite/ims/module/iam/internal/registration/CustomerProfileIntegrationTest.java | Rollback/recovery, MANDATORY/idempotency/conflict and module boundary |
| M | src/test/java/com/aquasuite/ims/module/iam/internal/registration/RegisterApiIntegrationTest.java | Profile values, no profile before activation |
| M | src/test/java/com/aquasuite/ims/module/iam/internal/registration/RegisterOtpIntegrationTest.java | No orphan profile after activation failure |
| M | src/test/java/com/aquasuite/ims/module/iam/internal/registration/RegisterConcurrencyIT.java | Exactly one profile under concurrent verify |
| M | src/test/java/com/aquasuite/ims/module/iam/internal/registration/RegisterMigrationIT.java | V6->V7 no guessed backfill, FK enforcement |
| M | src/test/java/com/aquasuite/ims/support/FlywayMigrationIT.java | Latest 7 and baseline V2->V7 5 migrations |
| M | src/test/java/com/aquasuite/ims/integration/AuthenticationUpgradeIT.java | V3->V7 4 migrations, data preserved |

Existing docs/spec/code guide/runbook/README/handoff and direct-user authorization are updated for v4. Spec AC10-11 add profile correctness, atomic rollback, recovery/idempotency and architecture checks. Existing AC01-09 remain with current V7 migration chain. Preserve V1-V6 bytes.

Verification: focused customer/registration tests first; then ./mvnw.cmd clean -Pphase1-it verify on disposable DB/Redis and mock mail. Inspect all Surefire/Failsafe XML including new suite; no reuse of historical 272 count as current proof. No live migration, commit, stage or push.
