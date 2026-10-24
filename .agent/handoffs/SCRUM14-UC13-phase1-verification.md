# SCRUM14 / UC-13 — Phase 1 và Flyway handoff

PLAN_ID: SCRUM14-UC13
PLAN_VERSION: 0.9.6
SPEC_VERSION: 0.9.1
PHASE: 1 — Core quản trị, chưa Auth/Audit
IMPLEMENTATION_STATUS: READY_FOR_REVIEW
CODE_REVIEW: PENDING

Người dùng yêu cầu dùng MapStruct, đơn giản hóa controller/payload, sau đó thêm comment tác dụng/luồng và xem lại việc gộp service. Plan 0.9.4 gộp SKU lookup vào BundleAdminService và thêm JavaDoc tiếng Việt. Refactor nằm trên branch feature/SCRUM-14-phase-1 sau baseline f9f8bc5; giữ HTTP URI/JSON và phạm vi Phase 1. Yêu cầu tiếp theo chuyển toàn bộ migration sang Flyway (plan 0.9.6); không stage/commit/push.

## Lanes Completed

| Lane | Kết quả |
|---|---|
| MapStruct | Interface Spring mapper, unmappedTargetPolicy ERROR; processor/Lombok binding dùng chung cho compile/testCompile |
| Tách nghiệp vụ | BundleDetailAssembler tính giá/sellable rồi gọi mapper; mapper chỉ chuyển field detail/summary/item/mutation/SKU |
| Controller | Gộp SKU lookup vào BundleAdminController; giữ /api/v1/catalog/admin/bundles và /api/v1/catalog/admin/bundle-skus |
| Application service | Controller gọi một BundleAdminService cho CRUD và searchSkus; xóa BundleSkuQueryService, lookup vẫn read-only |
| JavaDoc | Trước controller/service/assembler/mapper và toàn bộ DTO combo có tác dụng, caller và luồng; thêm giải thích khóa/version, xóa mềm, strict JSON và tính giá |
| Query | Đổi query thành validateQueryParameters; giữ chặn parameter lạ/lặp, tên biến dễ đọc |
| Payload | BundleItemRequest và BundleJsonDeserializers dùng chung; request create/update, response summary/detail giữ riêng theo contract |
| Đọc code | Đổi tên helper service thành validateRequest/loadDetail/loadDetails/findBundleProduct/lockConfiguration/replaceItems/markConfigurationUpdated; dùng payload/product/configuration trong CRUD |
| Test | Thêm 9 assembler/mapper unit cases, 2 controller cases và 1 HTTP case; các test hiện có tiếp tục chạy |
| Flyway | V1 schema nền; V2 combo preflight/DDL/backfill/verify; bỏ runner và SQL thủ công, dùng flyway_schema_history |
| Migration tests | Dùng Flyway cho checksum, rollback, constraints, baseline, giữ dữ liệu và retry |

## Files Changed

Repository: BE (aquasuite-be).

- pom.xml: MapStruct 1.6.3, lombok-mapstruct-binding 0.2.0; Flyway core/PostgreSQL do Boot quản lý.
- src/main/resources/application.yml; db/migration/V1__Initial_legacy_schema.sql, V2__SCRUM14_phase1_combo_core.sql.
- src/test/java/com/aquasuite/ims/support/FlywayMigrationIT.java; module/catalog/internal/migration/BundleCoreMigrationIT.java.
- Xóa scripts/db/Apply-Uc13Phase1.ps1, ba SQL trong src/main/resources/db/manual/, test fixture uc13-core-baseline.sql và docs/migrations/SCRUM14-UC13-phase1.md; dùng docs/migrations/flyway.md.
- src/main/java/com/aquasuite/ims/module/catalog/internal/mapper/BundleCoreMapper.java.
- internal/service/BundleDetailAssembler.java (mới), BundleAdminService.java, BundlePricingService.java; xóa BundleSkuQueryService.java.
- internal/controller/BundleAdminController.java; xóa BundleSkuController.java.
- internal/payload/BundleItemRequest.java và BundleJsonDeserializers.java (mới); BundleCoreCreateRequest.java, BundleCoreUpdateRequest.java, BundleCoreStatusRequest.java; thêm JavaDoc cho BundleCoreSummary/Detail/MutationResult, BundlePage và BundleSkuResponse.
- src/test/java/com/aquasuite/ims/module/catalog/internal/service/BundleDetailAssemblerTest.java (mới), BundleAdminServiceIT.java, BundleCoreHttpIT.java.
- src/test/java/com/aquasuite/ims/module/catalog/internal/controller/BundleAdminControllerTest.java; support/BundleCoreTestSupport.java; architecture/BundleCoreBoundaryTest.java.
- docs/README.md, docs/specs/SCRUM14-UC13-product-combos.md, docs/SCRUM14-UC13-phase1-code-guide.md; plan/handoff hiện hành.

Các đường internal trong bảng này nằm dưới src/main/java/com/aquasuite/ims/module/catalog/. Generated mapper nằm trong target/generated-sources, không track Git. Flyway là cơ chế duy nhất để thay đổi schema; Hibernate chỉ validate.

## Implementation Summary

BundleCoreMapper không còn gọi pricing hoặc tự xác định sellable. Assembler nhận các entity đã đọc và map ID, giữ nguyên component thiếu/inactive trong response, dùng giá SKU hiện tại và báo priceIssue đúng. Service vẫn sở hữu validation/transaction/khóa/version, không thêm truy vấn từ mapper/assembler.

Gộp controller chỉ thay tổ chức Java. Helper query giữ HTTP 400 SYS_002 cho query sai/lặp; bỏ nó sẽ cho phép Spring bỏ qua một số đầu vào ngoài contract. Hàm không phải Auth hoặc điều kiện nghiệp vụ mới.

Gộp CRUD và lookup vào BundleAdminService để giảm số application service cùng màn hình và dùng chung repository/mapper/giới hạn phân trang. BundlePricingService giữ riêng vì validation ghi và assembler đọc cùng cần công thức giá. BundleDetailAssembler là helper ghép response, không mở transaction/use case riêng. JavaDoc mô tả ranh giới và luồng gọi để việc giữ các helper này có lý do rõ ràng.

Create/update trùng 7 field nhưng create cấm expectedVersion, update bắt buộc có; list/detail trùng 14 field nhưng list không trả items/description. Giữ DTO riêng để ràng buộc rõ, dùng MapStruct loại boilerplate mapping. Dùng chung item request và strict numeric deserializers để update/status không phụ thuộc DTO create. Không thêm inheritance/validation groups/JSON lồng khác contract.

## Tests Added / Updated

- BundleDetailAssemblerTest: 9 cases kiểm field từ các source đúng, summary copy, giá SRS, enum/version/timestamp, variant thiếu/inactive, parent inactive/deleted/BUNDLE/thiếu, giá giảm không tạo discount âm và mapping mutation/lookup.
- BundleAdminControllerTest: thêm 2 parameterized cases cho query lạ/lặp ở cả hai URL; cập nhật constructor controller gộp.
- BundleCoreHttpIT: create từ chối expectedVersion, update bắt buộc có version, item string quantity/unknown field vẫn bị từ chối và không ghi dữ liệu.
- Test support import generated Spring mapper và assembler, bỏ controller cũ. ArchUnit kiểm cả generated mapper/assembler.
- Test support chạy Flyway rồi Hibernate validate tất cả entity (gồm inventory); không dùng create-drop.
- Combo migration IT không gọi PowerShell/psql hoặc đọc SQL thủ công.
- Các case giá, concurrency, rollback, status/delete, migration/checksum và import guard cũ vẫn là regression suite.

## Verification Performed

Môi trường: Java 21, Boot 3.5.16, PostgreSQL 17.6 qua Docker/Testcontainers. Mockito agent từ Maven cache và PSModulePath hệ thống theo lệnh sau; không dùng credential thật hoặc DB production.

```powershell
$env:PSModulePath = 'C:\Windows\System32\WindowsPowerShell\v1.0\Modules;' + $env:PSModulePath
.\mvnw.cmd clean -Pphase1-it '-DargLine=-javaagent:C:/Users/quang/.m2/repository/org/mockito/mockito-core/5.17.0/mockito-core-5.17.0.jar' verify
```

| Bằng chứng | Kết quả |
|---|---|
| Baseline f9f8bc5, uc13-phase1-precommit-verify.log | 36 unit + 37 integration = 73 PASS, lịch sử trước refactor |
| uc13-mapstruct-compile.log | Compile PASS; generated mapper được đối chiếu field/null/enum/timestamp |
| uc13-mapstruct-verify.log | 47 unit + 38 integration = 85 PASS, 0 failures/errors/skipped |
| uc13-mapstruct-clean-verify.log | PASS: 47 unit + 38 integration = 85 cases, 0 failures/errors/skipped, BUILD SUCCESS ngày 2026-10-20 lúc 10:08:43 +07:00 |
| uc13-service-comments-verify.log | PASS sau gộp service/JavaDoc: 47 unit + 38 integration = 85 cases, 0 failures/errors/skipped, BUILD SUCCESS ngày 2026-10-20 lúc 11:28:39 +07:00 |

Log local được ignore, lệnh trong handoff đủ để tái lập. Không cộng baseline hoặc nhiều lần chạy vào số test case cuối. Clean verify bao gồm compile/testCompile/package, sinh mapper từ đầu và loại class controller cũ; không khẳng định kết quả chỉ dựa trên class còn lại từ build trước.

Bằng chứng lịch sử trước Flyway: XML Surefire/Failsafe xác nhận 47/38 test, không lỗi/skipped; JAR có BundleCoreMapperImpl/BundleAdminController và không còn BundleSkuController. Kiểm 5 tài liệu, 42 liên kết tương đối, 7 JSON examples, code fences và encoding đều đạt; git diff --check PASS.

Lần kiểm sau gộp xác nhận thêm BundleSkuQueryService không còn trong JAR/source/test; controller chỉ phụ thuộc BundleAdminService. Không thêm test chỉ để kiểm comment.

## Contract Verification

URI/body/response/error code của B03-B06 Core, B11/B12/C01 giữ nguyên. Không nhận expectedVersion trên create; update/status vẫn strict Long, quantity strict integer, giá JSON numeric. List vẫn summary, detail vẫn giữ item kể cả SKU không sellable. Header timestamp/version, stale 409, deleted 404/no-op DELETE và transaction rollback giữ nguyên. Không thêm Auth/Audit hoặc public browse/publish/Sales.

## Deviations From Approved Plan

Refactor MapStruct và bổ sung JavaDoc/gộp service được người dùng trực tiếp yêu cầu, ghi trong plan 0.9.3/0.9.4. Không tạo review APPROVED thay reviewer. Flyway là yêu cầu trực tiếp tiếp theo của người dùng, ghi trong plan 0.9.5/0.9.6; không đổi HTTP contract. Không khôi phục review/handoff cũ đã được yêu cầu xóa.

## Known Limitations

Chưa có code review APPROVED/Final Gate hoặc triển khai live DB. Auth/Audit, public/media/tồn/options/Sales/preorder/reservation thuộc phase sau. MapStruct code được sinh khi build; IDE cần annotation processing. Cấu hình môi trường vẫn dùng JDBC/SMTP env và schema mặc định validate từ baseline.

## Tiếp nối

[Plan/tiến độ](../plans/SCRUM14-UC13.md), [spec đầy đủ](../../docs/specs/SCRUM14-UC13-product-combos.md), [giải thích code/controller/DTO](../../docs/SCRUM14-UC13-phase1-code-guide.md). Chỉ giữ handoff mới nhất trong thư mục này theo yêu cầu người dùng. Bước tiếp theo sau clean verify là review diff refactor; chưa có commit/push mới.

## Flyway hiện tại (plan 0.9.6)

Flyway tự chạy V1/V2 khi startup, trước Hibernate validate. Runner và SQL thủ công đã được bỏ hoàn toàn; chỉ còn db/migration trong runtime resources. Flyway quản lý lịch sử/checksum/transaction. Không sửa migration đã chạy trên database dùng chung; thêm V3 trở đi.

Database mới chạy tự động. Database đã có schema cần đối chiếu/backup và baseline một lần bằng Flyway: version 1 nếu chỉ có schema nền, version 2 nếu đã đủ schema combo. Baseline không sửa schema thiếu. Xem [runbook](../../docs/migrations/flyway.md). Không chạy migration vào database local hoặc production trong tác vụ này.

PASS: clean phase1-it verify BUILD SUCCESS at 2026-10-20 13:36:48 +07; 47 Surefire + 41 Failsafe = 88 PASS, zero failures/errors/skipped. Evidence: flyway-only-verify.log (ignored local log). Relative documentation links and git diff --check passed. Packaged JAR contains only Flyway SQL under db/migration, with no db/manual resources.
