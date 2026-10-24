# SCRUM08 customer account/profile confirmation

SPEC_ID: SCRUM08-UC01
SPEC_VERSION: 0.4.0
PLAN_VERSION: 4
IMPLEMENTATION_AUTHORIZED: YES_BY_DIRECT_USER_INSTRUCTION
INDEPENDENT_REVIEW: NOT_PERFORMED

User nhắc rõ “register ở đây là customer account mà” và xác nhận “Có, tạo cả hồ sơ customers” khi được hỏi có tạo hồ sơ liên kết tài khoản không. Đây là authorization bổ sung CRM profile tối thiểu theo Database.pdf trang 2, thay phần hoãn CRM trong spec 0.3.0/plan v3.

User UUID và role CUSTOMER vẫn là authentication identity. customers là hồ sơ nghiệp vụ CRM riêng, user_id UUID scalar/unique FK; profile và ACTIVE commit cùng transaction sau OTP. Lỗi CRM rollback cả activation/profile, resend phục hồi. HTTP/token/OTP flow giữ nguyên; không mở rộng projects/addresses/audit/config/jobs.

Không dùng lại approval/hash của bản trước cho byte mới. Bản 272 tests PASS ở handoff trước là evidence lịch sử trước bổ sung CRM; verification mới phải bao gồm profile correctness/rollback/idempotency/FK/boundary và regression toàn bộ. Không stage/commit/push hoặc sửa archive.
