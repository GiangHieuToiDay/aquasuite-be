# FEATURE-006 v0.4.0 Independent Technical Spec Re-Review

FEATURE_ID: FEATURE-006
SPEC_VERSION: 0.4.0
SPEC_SHA256: ffb9466927e36cf545350b6e68d9fabcaf5181dc7d90da0aa9ef45e3d3faca59
SPEC_REVIEW: CHANGES_REQUIRED
REVIEW_SCOPE: INDEPENDENT_TECHNICAL
PROJECT_APPROVAL: PENDING

## Review Boundary

Verdict này chỉ áp dụng cho đúng version và SHA-256 trên. Đây không phải approval của Product Owner/Lead Architect. Giữ nguyên `FEATURE-006-v0.3.2-approval.md`, Plan v2, review theo hash `dd42e9fe...`, source, migrations, tests và mọi thay đổi chưa commit. PostgreSQL tests không được chạy trong technical Spec Review này; không AC nào được tuyên bố PASS.

## Previous Findings Status

- **RESOLVED — Current implementation:** Spec dòng 68–70 phân biệt implementation v0.3.2 (V1, join entities/IDs, repositories, PostgreSQL tests đã được tạo) với target v0.4.0. Trạng thái V1 đã apply ở môi trường cần bảo toàn vẫn được ghi là chưa xác minh.
- **PARTIALLY RESOLVED — Migration wording:** Dòng 209 mới nêu rõ schema cuối sau tất cả Flyway migrations: `public.user_roles` chỉ có `user_id`, `role_id`, composite PK và hai FK; `assigned_at`/`assigned_by` cùng FK/index liên quan phải vắng mặt. Nhưng câu tuyệt đối ở dòng 198 vẫn chưa được thay hoặc giới hạn theo nhánh migration, nên blocker cũ còn hiệu lực.
- **RESOLVED — AC-04 verification precision:** AC-04 dòng 354 và matrix dòng 376 chỉ rõ bốn uniqueness cases riêng (`username`, `email`, `role_name`, `permission.code`), concurrent case và hai whitespace cases riêng chỉ cho `username`/`email`; không suy rộng whitespace constraint sang hai business keys còn lại.

## Blocking Findings

1. **BLOCKING — V1 content rule vẫn mâu thuẫn với nhánh forward migration.** Spec dòng 198 yêu cầu “Baseline migration chỉ được chứa các cột đã chốt trong Proposed Baseline Schema”. Dòng 208 lại bắt buộc giữ nguyên nội dung/checksum `V1__create_iam_foundation.sql` nếu đã apply ở bất kỳ môi trường cần bảo toàn nào, rồi thêm forward migration. V1 hiện tại, dòng 53–54, chứa `assigned_at` và `assigned_by`. Dòng 209 chỉ quy định *schema cuối cùng* và không loại bỏ yêu cầu tuyệt đối với nội dung baseline migration tại dòng 198. Nếu V1 đã apply, Plan v3 không thể vừa tuân thủ dòng 198 vừa tuân thủ dòng 208. **Yêu cầu sửa:** giới hạn quy tắc “V1 chỉ chứa các cột schema đích” vào nhánh có bằng chứng V1 chưa apply ở mọi môi trường cần bảo toàn; trong nhánh đã-applied, giữ nguyên V1 và dùng forward migration để đạt effective schema tại dòng 209. Không thay đổi quyết định Product Owner về schema đích.

## Warnings

- `V1_APPLICATION_STATE` vẫn là prerequisite cho Plan v3, không phải blocker của việc soạn Spec: phải inventory `flyway_schema_history` và schema catalog ở mọi môi trường cần bảo toàn trước khi chọn sửa V1 hay thêm forward migration. Không reset database, xóa history hoặc chạy `repair` mặc định.
- Docker/Testcontainers và isolated PostgreSQL fallback chưa được xác minh trong review này. AC-09 chỉ đạt khi thực sự chạy PostgreSQL suite; H2 hoặc unit tests không thay thế.
- Implementation hiện tại còn join entities/IDs và metadata theo v0.3.2; đây là trạng thái chuyển tiếp được Spec ghi nhận, không phải lỗi implementation của revision v0.4.0. Plan v2 và handoff cũ stale đối với hash mới.

## Architecture And Scope Review

- Target architecture tại dòng 54–58 và 230–270 nhất quán: đúng ba entities (`UserEntity`, `RoleEntity`, `PermissionEntity`), `UserStatus` ở `iam.internal.enums`, hai owning unidirectional `@ManyToMany(fetch = LAZY)` qua hai join tables, không inverse collections, join entities/embedded IDs hoặc cascade `PERSIST`/`REMOVE`/`ALL`.
- Effective PostgreSQL schema có năm bảng; ba UUID PK do application cấp, hai composite PK, FK `RESTRICT`, reverse-lookup indexes, case-insensitive unique indexes, `password_hash`, enum CHECK/default và database-generated `created_at`/`Instant` mapping được mô tả. Hai join tables chỉ có hai FK columns trong schema đích.
- Phạm vi chỉ gồm persistence foundation cho nhân viên. Public DTO/service skeleton không đổi; không thêm login, JWT, Redis, activation, RBAC enforcement, controller endpoint, provider/verification/FCM baseline hoặc production DTO/mapper speculative. Sáu protected skeleton files khớp baseline SHA-256 đã ghi trong Plan v2; Plan v3 phải hậu kiểm byte-for-byte.
- Recovery phân biệt transactional rollback với partial/non-transactional state, yêu cầu đối chiếu Flyway history và schema catalog, không mặc định reset/repair. Approval evidence v0.3.2 không được diễn giải là approval cho revision này.

## AC Coverage

Có đúng 17 Acceptance Criteria (`AC-01`–`AC-17`) và 17 verification mappings tương ứng. Mỗi AC có verification method, target và pass/fail criteria; AC-04 có bốn uniqueness và hai whitespace cases riêng. Coverage này chỉ đánh giá tính kiểm chứng của Spec, không xác nhận implementation/tests đã đạt. Blocker về wording migration ảnh hưởng cách Plan v3 triển khai và chứng minh AC-02/AC-03 trên nhánh V1 đã-applied.

## Next Action

CODEX_PLAN sửa mệnh đề tại dòng 198 để hai nhánh migration cùng hợp lệ, phát hành SHA-256 mới và yêu cầu independent re-review. Chỉ sau technical review đạt và Product Owner/Lead Architect xác nhận Spec revision mới được lập/review Plan v3. Không tiếp tục implementation theo Plan v2.
