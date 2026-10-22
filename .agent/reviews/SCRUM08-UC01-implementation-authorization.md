# SCRUM08 implementation authorization

PLAN_ID: SCRUM08-UC01
PLAN_VERSION: 4
PLAN_SHA256: 36627f8b3da19fbb303c073bbba9276514c7bb5a44210134277fdb34773561ca
SPEC_VERSION: 0.4.0
IMPLEMENTATION_AUTHORIZED: YES_BY_DIRECT_USER_INSTRUCTION
INDEPENDENT_PLAN_REVIEW: NOT_PERFORMED

Người dùng trực tiếp yêu cầu: “thực hiện plan đi”, sau khi nhận và thu gọn plan v2 về User/validation/email OTP activation. Chỉ đạo này cấp quyền thực thi plan trong hội thoại, theo nguyên tắc user authorization ưu tiên local workflow. Không giả lập independent Plan Review hoặc ghi canonical approval chưa thực hiện. Snapshot metadata trong active plan giữ nguyên; hash trên xác định chính xác plan được thực hiện.

Phạm vi vẫn theo file matrix plan v2; không CRM/audit/cleanup/config riêng, không stage/commit/push hoặc migration trên DB live. Code Review/Final Gate phải dựa trên handoff và verification thực tế, không suy ra từ chỉ đạo triển khai này.

Compatibility revision v3 cập nhật ba test đang có (IamMigrationIntegrationTest, FlywayMigrationIT, AuthenticationUpgradeIT) để kiểm full_name, phone uniqueness và số migration V6 đúng, không sửa HTTP contract/business scope. V2 được User yêu cầu triển khai có hash `0638425deaa5f05fe2ee1780fb41b6da26e1a2349a2533456a900f8de26fd41d`; hash metadata hiện tại trỏ v3 để truy vết thực thi, không khẳng định đã có technical approval độc lập cho revision mới.

Current revision 4: user explicitly confirmed “Có, tạo cả hồ sơ customers”. See SCRUM08-UC01-customer-profile-confirmation.md. The profile is now mandatory alongside activation; current hashes identify this scope. Earlier v2/v3 authorization remains historical evidence, not independent technical approval.
