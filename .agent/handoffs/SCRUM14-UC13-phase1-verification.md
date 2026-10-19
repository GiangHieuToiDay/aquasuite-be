# SCRUM14 / UC-13 — Phase 1 code alignment and verification

PLAN_ID: SCRUM14-UC13
PLAN_VERSION: 0.9.2
SPEC_VERSION: 0.9.1
PHASE: 1 — Iteration 1 core
IMPLEMENTATION_STATUS: READY_FOR_REVIEW
CODE_REVIEW: PENDING

Ngày thực hiện: 2026-10-17. Người dùng trực tiếp yêu cầu đối chiếu code/spec, implement và test, rồi cập nhật tiến độ. Thẩm quyền thực hiện này không được ghi thành independent review APPROVED. Phạm vi vẫn là core không Auth/Audit; không triển khai Phase 2/3 hoặc thay hợp đồng công khai.

## Lanes Completed

| Lane | Kết quả |
|---|---|
| Đối chiếu core | Đã đọc spec Core DTO/B03-B06/B11/B12/C01, service/controller/DTO/repository/mapper/pricing, migration và các test hiện có |
| Baseline | Full verify trước sửa đạt 35 unit + 25 integration tests, không lỗi/skipped |
| Test hồi quy | Test mới tái hiện update combo DELETED bằng version cũ trả 409 thay vì 404 |
| Implementation | Lấy aggregate lock → kiểm Product tồn tại/loại/trạng thái → kiểm version; chỉ DELETE cho phép đọc tombstone để retry |
| Bổ sung test | Thêm 12 test case, gồm parameterized cases, về deleted/rollback/reference/published/filter/paging |
| Verification cuối | PASS: 35 Surefire + 37 Failsafe, 0 failures/errors/skipped; BUILD SUCCESS |

## Files Changed

| Repository | Relative Path |
|---|---|
| BE | `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleAdminService.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleCoreHttpIT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleAdminServiceIT.java` |
| BE | `src/test/java/com/aquasuite/ims/module/catalog/internal/controller/BundleSkuControllerIT.java` |
| BE | `.agent/plans/SCRUM14-UC13.md` |
| BE | `.agent/handoffs/SCRUM14-UC13-phase1-verification.md` |
| BE | `docs/specs/SCRUM14-UC13-product-combos.md` |
| BE | `docs/README.md` |
| BE | `docs/SCRUM14-UC13-phase1-code-guide.md` |
| BE | `docs/migrations/SCRUM14-UC13-phase1.md` (liên kết bằng chứng, không đổi SQL/runner) |

## Implementation Summary

Core đã có list/detail/create/update/soft-delete/status/SKU lookup, pricing, validation và aggregate transaction/version. Lỗi xác nhận được là thứ tự kiểm stale che mất 404 của B06/B12 với combo DELETED. Sửa helper khóa để loại resource không tồn tại/không BUNDLE/đã xóa trước kiểm version khi update/status. DELETE vẫn thấy tombstone: version cũ trả 409, version hiện tại trả 200 no-op và giữ dữ liệu.

Không đổi URI, DTO, error code, schema hoặc dependency. Không gọi IAM/Audit/Inventory/Cloudinary/Sales từ core. Không sửa migration/checksum, ứng dụng legacy, review hoặc handoff lịch sử.

## Tests Added / Updated

| Test | Nội dung mới | Spec / AC core |
|---|---|---|
| BundleCoreHttpIT | Update/status DELETED với version cũ/mới trả SYS_004; DELETE stale/no-op đúng | B06/B11/B12; AC09/16/17 subset |
| BundleCoreHttpIT | List lọc ACTIVE/publication/keyword, paging sau loại NORMAL/DELETED, không cho query DELETED | B03; R01 quản trị, AC14 core |
| BundleAdminServiceIT | Constraint lỗi khi replace sau xóa item cũ phải khôi phục definition/items/version/timestamp | B06; AC01/09 và transaction |
| BundleAdminServiceIT | 7 ca SKU thiếu/inactive, parent inactive/deleted/BUNDLE, category thiếu/inactive; không tạo aggregate một phần | B05; AC10 |
| BundleAdminServiceIT | PUBLISHED không được thay definition, dữ liệu/version giữ nguyên | B06; AC14 core |
| BundleSkuControllerIT | Loại SKU inactive trước paging, tìm ký tự % theo nghĩa literal, loại parent BUNDLE | C01; AC13 lookup |

Các test cũ tiếp tục kiểm CRUD không token/không audit, giá SRS/overflow, strict JSON quantity/version, item ID, identical PUT, no-op/status/delete, tranh chấp ghi, migration và import guard. Không đánh dấu AC08 hoặc phần audit của AC11/16/17 PASS vì chúng deferred.

## Verification Performed

Lệnh chạy PowerShell tại Git root:

```powershell
$env:PSModulePath = 'C:\Windows\System32\WindowsPowerShell\v1.0\Modules;' + $env:PSModulePath
.\mvnw.cmd -Pphase1-it '-DargLine=-javaagent:C:/Users/quang/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' verify
```

Đường dẫn Mockito agent là đường dẫn cache Maven cục bộ, cần điều chỉnh theo máy khi tái lập. Java 21/Boot 3.5.16; Docker/Testcontainers PostgreSQL 17.6. Sandbox không truy cập được Docker pipe nên chạy test ngoài sandbox theo approval công cụ; không dùng DB production.

| Lần chạy | Log tại Git root | Kết quả |
|---|---|---|
| Baseline trước sửa | `uc13-iteration1-baseline-verify.log` | PASS, 35 Surefire + 25 Failsafe, BUILD SUCCESS |
| Regression trước sửa | `uc13-iteration1-regression-before-fix.log` | EXPECTED_FAIL: 1 test hồi quy fail với expected 404 / actual 409; 8 pricing unit tests PASS |
| Full verify sau sửa | `uc13-iteration1-final-verify.log` | PASS, 35 Surefire + 37 Failsafe = 72 test case trong một lần chạy; 0 failures/errors/skipped, BUILD SUCCESS lúc 22:21:40 +07:00 ngày 2026-10-17 |

Để tái lập lần hồi quy trước sửa, cùng lệnh verify có thêm `'-Dtest=BundlePricingServiceTest'` và `'-Dit.test=BundleCoreHttpIT#deletedBundleCannotBeUpdatedOrToggledEvenWithOldVersion'`. Log trước sửa giữ làm bằng chứng, không phải trạng thái cuối của code.

Full verify đã gồm compile/testCompile/package, HTTP không token, PostgreSQL thật, concurrency, migration runner/backfill/checksum/rollback và import regression. Kết quả mới thay thế hạn chế “chưa chạy lại suite” của đợt tài liệu 0.9.1; không dùng các lần chạy lại hoặc baseline để cộng vào 72 test case cuối. Core sẵn sàng code review; chưa có code review APPROVED hoặc Final Gate.

PASS kiểm tra sau cập nhật tiến độ: 6 tài liệu, 49 liên kết tương đối tồn tại, code fences cân bằng, 7 ví dụ JSON parse được, không trailing whitespace/ký tự lỗi encoding; `git diff --check` PASS. Đã đối chiếu trực tiếp XML Surefire/Failsafe với số 35/37 và log final BUILD SUCCESS (log PowerShell dùng UTF-16). Không stage/commit/push.

## Contract Verification

| Contract | Đối chiếu |
|---|---|
| B03/B04 | List/detail và giá hiện tại, không public availability; loại DELETED/NORMAL, lọc và phân trang |
| B05/B06 | Core DTO validation; lưu Product/config/items nguyên tử; giữ mã và item ID; tăng version khi PUT, cấm edit PUBLISHED |
| B11/B12 | Xóa mềm/đổi status, không tự publish; giữ code/items; stale/no-op và 404 tombstone theo từng action |
| C01 | Chỉ SKU ACTIVE của Product NORMAL ACTIVE; không costPrice/tồn; không token |
| Auth/Audit | Chưa tích hợp theo yêu cầu Iteration 1; không tạo audit CRUD hoặc User runtime giả |

## Deviations From Approved Plan

Không thay scope Phase 1 hoặc public contract. Plan 0.9.2 ghi thẩm quyền triển khai trực tiếp từ người dùng; review/hash 0.8.1 không còn áp dụng. Chưa có independent review mới; không tự cấp approval hoặc Final Gate.

## Known Limitations

Chưa deploy hoặc kiểm live DB. Phase 2/3, Auth/Audit và lịch sử before/after vẫn deferred. Các field timestamp/status/version không thay thế audit history. Phần full UC R01-R09 chưa hoàn tất chỉ vì core đạt kiểm chứng.

## Tiếp nối

### Chuẩn bị đưa Phase 1 lên Git — 2026-10-19

Theo yêu cầu trực tiếp của người dùng, tạo branch `feature/SCRUM-14-phase-1` để add/commit/push cả source, tests, migration, docs và workflow artifacts hiện hành. Tiêu đề commit: `feat(catalog): implement SCRUM-14 phase 1 combo management`. Quyền Git này không đồng nghĩa code review hoặc Final Gate đã được duyệt.

Đã loại credential hardcode/fallback khỏi cấu hình common, dùng `SPRING_DATASOURCE_*` và `MAIL_USERNAME`/`MAIL_PASSWORD`; JPA mặc định `validate`. Cập nhật thêm `.env.example`, `application.yml`, `application-prod.yml`, `docs/configuration/spring-profiles.md` và test cấu hình. Runtime cần được cấp các biến JDBC/SMTP từ môi trường; Spring không tự đọc `.env`. Không thay SQL migration hoặc tích hợp Auth/Audit. Credential kế thừa đã có trong remote nhánh nguồn; việc rotate/revoke thuộc chủ sở hữu, không sửa Git history trong tác vụ này.

Lệnh full verify giữ như trên; log mới `uc13-phase1-precommit-verify.log` được ignore cùng các log local. **PASS: 36 Surefire + 37 Failsafe = 73 test case, 0 failures/errors/skipped; BUILD SUCCESS lúc 20:36:42 +07:00 ngày 2026-10-19.** Có thêm một regression test cấu hình so với 72 test ngày 2026-10-17. Đã kiểm tra các file chuẩn bị commit không còn credential kế thừa, log/target/.env riêng không nằm trong phạm vi stage.

[Plan và tiến độ](../plans/SCRUM14-UC13.md), [spec](../../docs/specs/SCRUM14-UC13-product-combos.md). Handoff này là bản mới nhất và duy nhất của UC-13 trong thư mục handoff sau khi dọn theo yêu cầu người dùng ngày 2026-10-19. Bốn handoff cũ và hai review 0.8.1 lỗi thời đã được xóa; archive giữ nguyên. Chưa có review mới. Các log baseline/regression/final nêu trên vẫn là bằng chứng kiểm chứng. Không sửa code hoặc chạy lại test trong lần dọn tài liệu này. Bước tiếp theo là code review core; không yêu cầu provider IAM/Audit để review CRUD.
