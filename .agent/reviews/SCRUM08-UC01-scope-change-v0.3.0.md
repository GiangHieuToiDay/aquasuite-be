# SCRUM08 / UC-01 - User scope change evidence

SPEC_ID: SCRUM08-UC01
SPEC_VERSION: 0.3.0
SPEC_REVIEW: APPROVED
APPROVAL_AUTHORITY: USER / PROJECT_OWNER
DATE: 2026-10-20
PLAN_ID: SCRUM08-UC01
PLAN_VERSION: 2
PLAN_REVIEW: PENDING
IMPLEMENTATION_AUTHORIZED: NO

## User instructions

Người dùng yêu cầu dùng lại token login đã có, chưa cần RegistrationCleanupJob/RegistrationProperties/RegistrationConfig, và “chỉ cần tập trung vào việc đăng ký User và validaton thôi”.

Khi làm rõ giữ OTP hay đăng ký trực tiếp, người dùng chọn “Vẫn giữ OTP email để kích hoạt”. Các quyết định trước giữ UUID, không Google, email OTP trước/SMS sau tiếp tục có hiệu lực.

## Scope revision

- Chỉ User registration + validation + email OTP activation; hoãn CRM, activity audit, terms persistence và rate-limit diện rộng.
- Tạo User PENDING_ACTIVATION, OTP đúng mới ACTIVE; chưa OTP không login được.
- Redis có TTL cho OTP, không cleanup job/scheduler/challenge table hoặc registration token/config/secret subsystem riêng.
- Dùng lại PasswordEncoder, JavaMailSender, Redis và JWT/session implementation đang có. AuthService dùng private issuance helper chung; không thêm token service/verifier hay JWT type.
- Bổ sung fullName vào User và unique phone là schema tối thiểu để giữ dữ liệu form/uniqueness. Đây là thiết kế kỹ thuật của plan v2, cần technical Plan Review trước code.
- Cơ chế anti-abuse giữ tối thiểu OTP attempts/cooldown; giới hạn pending User còn trong DB vì cleanup được bỏ theo yêu cầu. Không nhận là đầy đủ toàn SRS UC-01.

## Superseded artifacts

Spec 0.2.0 SHA-256 trước revision: `80cf62119754ade64a670f7f8c45fba9281e76f947da3a8e64dfb034db13713a`.

Plan v1 SHA-256 trước revision: `4d8fb7d9e71ed1aeb3b319694c69f7bd88ff5406030724249fa9876fa47c7f14`.

Approval evidence 0.2.0 được giữ nguyên làm lịch sử; không còn chứng minh current spec bytes. Spec/plan mới là nguồn phạm vi hiện tại. Không sửa archive hoặc tự nhận independent review. Chỉ các tài liệu đã được chỉnh sửa, Java/SQL/tests chưa triển khai.
SPEC_SHA256: 2ff30abd411d88ec5e3f065ca156716e2acd5b5c52991520f75b2dd5baa2670c
PLAN_SHA256: 0638425deaa5f05fe2ee1780fb41b6da26e1a2349a2533456a900f8de26fd41d
