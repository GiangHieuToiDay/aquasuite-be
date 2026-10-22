# FEATURE-006 v0.4.0 Final Independent Technical Spec Re-Review

FEATURE_ID: FEATURE-006
SPEC_VERSION: 0.4.0
SPEC_SHA256: d973404002d90a04ad9bdf7656b63d7ad68a4fbedf9808e05f811dc907b9ebe3
TECHNICAL_SPEC_REVIEW: APPROVED
PROJECT_SPEC_APPROVAL: PENDING

## Review Boundary

Đây là technical verdict độc lập cho đúng version và SHA-256 của `docs/specifications/FEATURE-006-iam-foundation.md` nêu trên; không phải approval của Product Owner/Lead Architect. Approval v0.3.2 và hai review `CHANGES_REQUIRED` theo các hash cũ được giữ nguyên làm lịch sử. Plan v2, Plan Review và handoff dựa trên v0.3.2 đã stale đối với Spec v0.4.0. Không sửa Spec, Plan, source, migration hoặc tests; không chạy PostgreSQL tests trong lượt review này.

## Previous Blocker Resolution

- **RESOLVED:** Spec dòng 198 nay quy định *schema cuối sau toàn bộ Flyway migrations*, không còn buộc V1 luôn khớp schema đích. Chỉ có thể sửa V1 khi có bằng chứng chưa apply ở bất kỳ môi trường cần bảo toàn nào; nếu đã apply thì giữ nguyên V1 và dùng forward migration; nếu chưa xác minh thì không sửa V1.
- Dòng 208 yêu cầu Plan v3 kiểm tra `flyway_schema_history` và schema catalog trước khi chọn nhánh; dòng 209 yêu cầu `public.user_roles` cuối cùng chỉ có `user_id`, `role_id`, composite PK và hai FK, không còn `assigned_at`, `assigned_by` hoặc FK/index liên quan. Dòng 210 cấm reset database/schema, sửa Flyway history hoặc repair mặc định. Các dòng 198, 202–210 nhất quán theo nhánh migration.
- V1 hiện tại vẫn có `assigned_at` và `assigned_by` theo implementation v0.3.2. Spec dòng 68–70 nhận diện đây là trạng thái chuyển tiếp, không coi migration file/test đã tồn tại là bằng chứng V1 đã apply ở môi trường cần bảo toàn.

## Blocking Findings

Không có blocking finding đối với nội dung Spec theo SHA-256 đã review.

## Warnings

- `V1_APPLICATION_STATE` chưa được xác minh ở các môi trường cần bảo toàn. Đây là điều kiện bắt buộc cho Plan v3 trước khi quyết định sửa V1 hoặc tạo forward migration; không tự giả định từ Git working tree hay kết quả test cũ.
- Docker/Testcontainers và isolated PostgreSQL fallback chưa được xác minh trong review này. AC-09 chỉ có thể PASS sau khi PostgreSQL integration suite được chạy thực tế; H2/unit tests không thay thế.
- Implementation hiện tại còn join entities/embedded IDs, repositories và migration metadata của v0.3.2. Đây là chênh lệch được Spec chủ động mô tả; chỉ Plan v3 đã duyệt mới được đăng ký refactor. Sáu protected skeleton files hiện khớp SHA-256 baseline Plan v2 và phải tiếp tục được hậu kiểm byte-for-byte.

## Architecture And Scope Review

- Target có đúng ba JPA entities (`UserEntity`, `RoleEntity`, `PermissionEntity`), `UserStatus` ở `iam.internal.enums`, đúng hai owning unidirectional `@ManyToMany(fetch = LAZY)` qua `user_roles` và `role_permissions`; không join entity/embedded ID, inverse collection hoặc cascade `PERSIST`/`REMOVE`/`ALL`.
- PostgreSQL effective schema có năm bảng, ba UUID PK do application cấp, hai composite PK, FK và reverse-lookup indexes. `user_roles` cuối cùng chỉ có hai FK columns. `password_hash`, bốn UserStatus values/default, case-insensitive unique indexes, `created_at` do database cấp và `Instant` JPA mapping vẫn được giữ.
- Foundation chỉ dành cho tài khoản nhân viên nội bộ. Không thêm login, JWT, Redis, activation, RBAC enforcement, HTTP endpoint, public operation, provider/verification/FCM fields hoặc production DTO/mapper speculative. Public contracts và sáu protected skeleton files phải giữ nguyên.
- Recovery phân biệt transactional rollback với partial/non-transactional state; yêu cầu đối chiếu Flyway history/catalog và approved recovery path, không mặc định `repair` hay destructive reset.

## AC Coverage

Có đúng 17 Acceptance Criteria (`AC-01`–`AC-17`) và 17 AC-to-Test verification mappings, mỗi AC có phương án, target và pass/fail criteria. AC-04 tách bốn PostgreSQL case-insensitive uniqueness cases (`username`, `email`, `role_name`, `permission.code`) cùng concurrent case và hai whitespace cases riêng (`username`, `email`). Coverage là đánh giá tính kiểm chứng của đặc tả, không xác nhận implementation hoặc tests đã PASS.

## Next Action

Chuyển đúng Spec version/hash và technical review evidence này cho Product Owner/Lead Architect quyết định project-level Spec approval. Không lập hoặc triển khai Plan v3 trước approval đó. Sau approval, CODEX_PLAN lập Plan v3 với inventory Flyway history/catalog, migration branch, exact file ownership và PostgreSQL verification; Plan v3 cần independent Plan Review mới.
