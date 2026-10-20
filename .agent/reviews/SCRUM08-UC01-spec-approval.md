# SCRUM08 / UC-01 - Human Spec Approval Evidence

SPEC_ID: SCRUM08-UC01
SPEC_VERSION: 0.2.0
SPEC_REVIEW: APPROVED
APPROVAL_AUTHORITY: USER / PROJECT_OWNER
APPROVAL_DATE: 2026-10-20
SPEC_PATH: docs/specifications/SCRUM08-UC01-register-customer-account.md
SPEC_SHA256: 80cf62119754ade64a670f7f8c45fba9281e76f947da3a8e64dfb034db13713a
PLAN_REVIEW: NOT_PERFORMED
IMPLEMENTATION_AUTHORIZED: NO

## Evidence from this conversation

User chấp thuận tài liệu: “chấp nhận tài liệu lên plan thực hiện đi”.

User chốt phạm vi tiếp theo: “Email OTP trước, SMS để sau”. Trước đó đã chốt UUID đúng, giữ nguyên và không Google.

Spec 0.1.0 được chấp thuận có SHA-256 `386b5e1f20ff252c708b6ef75179373efb492661ed21c416eed3dcaa2f1c07e4`. Revision 0.2.0 ghi approval, email-only/SMS deferred và prerequisite cấu hình điều khoản theo chính hội thoại này; không thay các endpoint, mã lỗi hoặc policy đã được duyệt. Nhãn “đề xuất” trong phần thiết kế chỉ phân biệt nguồn SRS với thiết kế kỹ thuật.

## Scope of approval

- Giữ UUID, không Google; email OTP 6 số/300 giây, resend 60 giây; SMS trả REG_011 và được hoãn.
- API đăng ký public theo allowlist, challenge persistence, quota, CRM/audit transaction và auto-login recovery theo spec.
- Chấp thuận đi tới bước lập implementation plan. Không giả lập independent review hoặc coi approval spec là approval hash của plan chưa tồn tại.
- Nội dung/mã phiên bản điều khoản thật và mail delivery configuration do chủ sản phẩm cung cấp trước sử dụng thực tế; không lưu credential trong artifact.

Plan Review tuân thủ `docs/workflow/feature-delivery.md`. Không sửa review lịch sử hoặc archive; không stage/commit artifact này.
