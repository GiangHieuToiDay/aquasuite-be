# FEATURE-006 v0.4.0 Independent Technical Spec Review

FEATURE_ID: FEATURE-006
SPEC_VERSION: 0.4.0
SPEC_SHA256: dd42e9fe6c903a6b3fb6787d1851e61ca8cd47d887d6717ee46ae95258c149b5
SPEC_REVIEW: CHANGES_REQUIRED
REVIEW_SCOPE: INDEPENDENT_TECHNICAL
PROJECT_APPROVAL: PENDING

## Review Boundary

Đây là technical review độc lập cho đúng byte của Spec có SHA-256 nêu trên, không phải quyết định phê duyệt của Product Owner/Lead Architect. Approval evidence v0.3.2 tại `docs/specifications/reviews/FEATURE-006-v0.3.2-approval.md` chỉ có hiệu lực cho version/hash cũ và không bị thay đổi. Plan v2 cùng implementation handoff theo v0.3.2 đã stale đối với revision này. Không chạy implementation hoặc PostgreSQL tests trong review này.

## Previous Blocker Status

- **RESOLVED — Current-state description:** Spec dòng 68–70 hiện phân biệt implementation theo v0.3.2 với kiến trúc đích v0.4.0. `src/main/resources/db/migration/V1__create_iam_foundation.sql` tồn tại và hiện chứa `assigned_at`, `assigned_by`; source có join entities/embedded IDs, repositories và PostgreSQL integration tests. Spec không suy diễn từ sự tồn tại của file/test rằng V1 đã được áp dụng ở môi trường cần bảo toàn.

## Blocking Findings

1. **BLOCKING — Mâu thuẫn về nội dung V1 trong nhánh forward migration.** Spec dòng 198 khẳng định “Baseline migration chỉ được chứa các cột đã chốt trong Proposed Baseline Schema”. Nhưng dòng 208–209 yêu cầu giữ nguyên nội dung/checksum V1 nếu V1 đã được áp dụng ở bất kỳ môi trường cần bảo toàn nào và dùng forward migration. V1 hiện tại, dòng 53–54, chứa `assigned_at` và `assigned_by`, không thuộc Proposed Baseline Schema dòng 152–175. Nếu nhánh đã-applied xảy ra, Plan v3 không thể vừa giữ nguyên V1 vừa bảo đảm V1 chỉ chứa các cột của schema đích. **Yêu cầu sửa:** thay mệnh đề tuyệt đối ở dòng 198 bằng quy tắc về *effective schema sau toàn bộ migrations*; chỉ áp dụng giới hạn cột trực tiếp cho V1 khi đã chứng minh V1 chưa apply ở mọi môi trường cần bảo toàn. Ghi rõ V1 cũ được giữ nguyên trong nhánh forward migration và migration mới loại bỏ metadata để đạt effective schema v0.4.0.

## Warnings

- **Operational prerequisite:** Chưa có evidence về `flyway_schema_history` và schema catalog của mọi môi trường cần bảo toàn. Spec dòng 68–70, 208–209 và 424–432 đã giao rõ việc inventory cho Plan v3; đây không phải blocker của technical Spec Review, nhưng là blocker trước quyết định sửa V1 hoặc tạo forward migration.
- **Verification precision:** AC-04 dòng 353 yêu cầu concurrent case-variant duplicate insert thất bại; matrix dòng 375 kiểm tra case variants nhưng chưa nói rõ chạy hai transaction đồng thời. Plan v3 nên chỉ định concurrency test hoặc giải thích evidence tương đương từ unique index PostgreSQL.
- **Test environment:** Docker/CI Testcontainers chưa được xác nhận trong review này. Spec dòng 298–311 yêu cầu kiểm tra prerequisite và fallback PostgreSQL cô lập; không được dùng H2 hay báo test PASS khi chưa chạy.

## Architecture And Data Review

- Kiến trúc đích tại dòng 54–58, 225–269 và AC-07 nhất quán: chỉ `UserEntity`, `RoleEntity`, `PermissionEntity`; `UserStatus` ở `iam.internal.enums`; hai owning unidirectional `@ManyToMany(fetch = LAZY)`; không join entity/embedded ID, inverse collection hoặc lifecycle cascade `ALL`/`REMOVE`/`PERSIST`.
- Schema đích tại dòng 152–186 có đúng năm bảng PostgreSQL; ba UUID PK do application cấp, hai composite PK, FK `RESTRICT`, indexes cho reverse lookup, case-insensitive unique indexes, `password_hash`, bốn status values và default `PENDING_ACTIVATION`. Hai join tables của *effective schema* chỉ còn hai FK columns; riêng nội dung V1 cần sửa diễn đạt theo blocking finding trên.
- Spec giữ database-generated `created_at` (`TIMESTAMPTZ`, `Instant`, read-only JPA mapping), deferred provider/verification/FCM data, sensitive-data boundary và out-of-scope login/JWT/Redis. Sáu protected skeleton files hiện khớp các baseline SHA-256 trong Plan v2; AC-12 yêu cầu Plan v3 ghi và hậu kiểm byte-for-byte.
- Migration recovery tại dòng 400–408 phân biệt transactional rollback với partial/non-transactional failure; kiểm tra Flyway history và schema catalog trước recovery, không reset database hay repair history mặc định.

## AC Coverage

Spec có đúng 17 acceptance criteria (`AC-01`–`AC-17`) và 17 dòng tương ứng trong AC-to-Test Verification Matrix, không thiếu ID. Các nhóm schema, entity/mapping, uniqueness, enum/default, timestamp, security/scope, skeleton protection, PostgreSQL verification và recovery đều có phương án kiểm tra. Đây là đánh giá khả năng kiểm chứng; không AC nào được tuyên bố PASS trong review này. Lưu ý về concurrency của AC-04 được ghi tại Warnings.

## Next Action

CODEX_PLAN sửa riêng mâu thuẫn ở dòng 198 mà không thay đổi quyết định Product Owner, phát hành hash mới rồi yêu cầu independent re-review. Product Owner/Lead Architect chỉ xác nhận Spec sau technical review đạt. Sau đó mới lập Plan v3 với inventory `flyway_schema_history`/schema catalog, file ownership và PostgreSQL verification; không tiếp tục implementation theo Plan v2.
