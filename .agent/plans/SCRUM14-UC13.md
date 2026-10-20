# SCRUM14 / UC-13 — Implementation Plan

- PLAN_ID: SCRUM14-UC13
- PLAN_VERSION / SPEC_VERSION: 0.11.0 / 0.11.0
- Owner: QuangNB
- STATUS: IMPLEMENTATION_AUTHORIZED_BY_USER; technical review PENDING; approval/hash 0.8.1 không áp dụng.
- IMPLEMENTATION_STATUS: READY_FOR_REVIEW; authentication merge verify 251 PASS; no independent approval or Final Gate.
- PRIORITY_PHASE: 2; user-authorized implementation, independent review pending.
- Baseline: Java 21, Spring Boot 3.5.16; com.aquasuite.ims.module.
- Spec: [UC13 đầy đủ](../../docs/specs/SCRUM14-UC13-product-combos.md).

## 1. Phạm vi mới và căn cứ

Người dùng yêu cầu core chỉ phục vụ quản lý thêm/sửa/xóa combo, chưa cần actor/JWT/phân quyền vì chờ feature chung từ người khác. Yêu cầu trực tiếp này thay các prerequisite IAM trong plan 0.8.1. Không cấp approval thay reviewer. Không sửa archive hoặc review artifact lịch sử.

Revision 0.9.1 ghi nhận xác nhận của người dùng về phạm vi ba phase, sửa tài liệu lệch và bổ sung tiến độ. Đây là kế hoạch tổng thể dẫn từ spec đầy đủ, không chỉ là checklist gỡ Auth. Source/test trong workspace đã có implementation 0.9.0; các bước dưới đây phân biệt việc đã có, bằng chứng lịch sử và phần còn lại. Không tự triển khai Phase 2/3.

Repository Map: `BE = D:\Workspace\aquasuite-be`, Git root duy nhất của tác vụ. Đường dẫn `D:\DO_AN\Aquasuite\aquasuite-be` trong governance là vị trí cũ; không thao tác tại đó. `aquasuite-fe` là repository độc lập, ngoài scope. Chỉ public contract mới trong `api/`, không import internal hoặc tạo JPA relationship chéo feature.

## 2. Đúng ba phase thực hiện

| Phase | Phạm vi | Kết quả cần đạt |
|---|---|---|
| 1 — Core quản trị | B03-B06 Core, B11/B12, C01: danh sách/chi tiết/tạo/sửa/xóa mềm/bật tắt/tìm SKU; giá, transaction và version | Hoạt động không cần token/actor/permission/IAM config; validation và rollback đầy đủ; timestamp/status/version được lưu |
| 2 — Đăng bán và lựa chọn | Public/publish/unpublish, media/ngày, đọc tồn/preview, nhóm option do Owner cấu hình | Fixed storefront trước options; chốt giá/whitelist; tích hợp IAM/Audit chung theo scope riêng khi provider sẵn sàng, không tự xây nền tảng |
| 3 — Giao dịch và thiếu hàng | Sales cart/POS/quotation/checkout, snapshot, substitute/preorder, reservation | Policy giá/hiệu lực/transaction đã chốt, tích hợp thực theo module sở hữu |

Không tuyên bố hoàn tất UC13 khi mới bàn giao core. Spec giữ toàn bộ yêu cầu; những phần deferred cần scope/test/review riêng trước triển khai.

### Ánh xạ từ spec đến công việc

| Hạng mục | Spec / AC áp dụng | Công việc và kết quả |
|---|---|---|
| P1.1 — Dữ liệu và repository | Mục 3.3, 4; AC15, phần dữ liệu AC01/10 | Dùng Product BUNDLE, BundleConfiguration, BundleItem; constraint/migration, preflight/backfill và query; không SKU/tồn combo |
| P1.2 — Tạo/sửa và giá | B05/B06 Core; AC01 phần CRUD, AC02/09/10 | Validation SKU/category/brand/qty/giá; lưu aggregate nguyên tử; expectedVersion và khóa; không IAM/Audit |
| P1.3 — List/detail/lookup | B03/B04 Core, C01; R01 phần quản trị, AC13 phần lookup, AC14 core | Tìm/lọc/phân trang, response DTO và giá hiện tại; không media/tồn/public browse |
| P1.4 — Xóa mềm/bật tắt | B11/B12; AC16/17 phần core, AC09 | Giữ code/items, ẩn DELETED, no-op/version, không publish hoặc tác động giao dịch |
| P1.5 — Contract và kiểm chứng | Mục 5 Core DTO, 6, 8; AC14 core/AC15 | HTTP không token, lỗi/envelope, rollback/concurrency, import guard, config và migration; handoff có bằng chứng |
| P2.1 — Đăng bán cố định | R01-R05; B01/B02/B07-B09, C02/C03; AC03-05/12, phần AC13/14 | Chốt public visibility/hiệu lực/media/Inventory read; triển khai, kiểm clock/asset/dependency và preview |
| P2.2 — Option | R06; componentGroups, B03-B06 mở rộng/B09; AC06, phần AC10/13/14 | Chốt D05-O/P, whitelist và giá option; review tương thích Core DTO trước mở rộng |
| P3.1 — Giao dịch | R07/R09; S01-S09 và API liên module; AC07, phần AC11/14 | Chốt policy giá/TTL/snapshot/thuế và ownership; cart/POS/quotation/checkout dùng chung contract |
| P3.2 — Thiếu hàng | R08; B10/resolution/preorder/reservation; AC05/06/07/13/14 phần giao dịch | Chốt D05-S, D06-T/P/E, D07-L/V/F; thay thế có xác nhận, preorder và vòng đời tồn có kiểm chứng |
| Tích hợp Auth/Audit sau core | Mục 2, CurrentActorApi/AuditApi; AC08, phần audit AC11/16/17 | Chờ provider chung và scope review riêng ở Phase 2/3; không dựng nền tảng trong P1, không dùng làm blocker CRUD |

### Thứ tự thực hiện và điều kiện vào/ra

- Phase 1: P1.1 → P1.2 → P1.3 → P1.4 → P1.5; có thể nghiệm thu core riêng. Dependency cần là catalog, PostgreSQL và môi trường test; không cần issuer, Cloudinary, Inventory hoặc Sales. Ra phase khi core contract/validation/transaction đạt, bằng chứng được review và handoff đúng revision. Hiện source đã có; xem tiến độ ở mục 7.
- Phase 2: chốt quyết định public/media/hiệu lực/tồn → P2.1 → chốt option/giá → P2.2. Có file scope, provider API và review riêng trước code; ra phase khi public/publish/option/preview được kiểm chứng, không coi preview là mua/giữ hàng.
- Phase 3: chốt policy và chứng minh transaction boundary → P3.1/P3.2 theo dependency Sales/Inventory → kiểm cart/POS/quotation/checkout và thiếu hàng. Không tạo toàn bộ Sales trong ticket Catalog; giao việc theo module sở hữu. Ra phase khi R07-R09 và vòng đời reservation/snapshot đạt bằng chứng tích hợp.
- Luồng thực hiện tuần tự trong repository này; không cần phân công agent hoặc tạo thêm tầng kỹ thuật để hoàn thành P1. Quyết định tương lai chỉ chặn phần bị ảnh hưởng.

## 3. Thay đổi core hiện tại

Các mục 1–6 mô tả thay đổi revision 0.9.0 đã có trong workspace; không phải yêu cầu thực hiện lại hoặc xóa thêm mã legacy.

1. Bỏ CurrentActorApi khỏi BundleAdminService và BundleSkuQueryService; bỏ kiểm requireActor.
2. Gỡ scaffolding UC13: CurrentActorApi/ActorDto/implementation, CatalogApiSecurityConfig/CatalogAuthProperties và JWT converter/error handlers. Giữ IAM/password legacy.
3. Gỡ resource-server/security-test dependency chỉ thêm cho UC13. Không thêm tài khoản giả hoặc cờ bypass quyền.
4. Tạm ngừng gọi AuditApi từ CRUD vì chưa có Actor ID; giữ module/bảng và lịch sử sẵn có, không sửa migration đã phát hành.
5. Giữ riêng tính giá, lookup, DTO, BundleConfiguration; giữ transaction, PESSIMISTIC_WRITE, expectedVersion/@Version, soft delete, import guard và validation.
6. HTTP integration test gọi trực tiếp không Authorization; thay rollback do audit bằng lỗi ghi BundleItem. Audit standalone test vẫn kiểm API audit độc lập.

## 4. File scope

Implementation hiện có: `module/catalog/internal/controller/BundleAdminController.java` và `service/BundleAdminService.java` cùng điều phối CRUD + lookup; `BundlePricingService` tính giá, `BundleDetailAssembler` ghép response, `mapper/BundleCoreMapper.java` dùng MapStruct, entities/repositories và các payload core/item/helper nằm trong catalog. Controller và service SKU cũ đã gộp; không sửa SQL đã phát hành.

Scope sửa lần đồng bộ 0.9.1 gồm spec, plan, handoff tài liệu khi đó, README, code guide, migration runbook và phần baseline hiện trạng kiến trúc. Handoff của đợt đó đã được bỏ theo yêu cầu dọn tài liệu ngày 2026-10-19; dùng implementation handoff hiện hành. Không sửa AGENTS.md, archive hoặc review lịch sử.

Danh sách dưới ghi lại scope thay đổi implementation 0.9.0 để truy vết:

- Catalog: BundleAdminService.java, BundleSkuQueryService.java trong internal/service.
- Xóa scaffolding IAM chỉ của UC13: api/CurrentActorApi.java, api/dto/ActorDto.java, internal/service/impl/CurrentActorApiImpl.java; internal/config/CatalogApiSecurityConfig.java/CatalogAuthProperties.java; internal/security/ApiAccessDeniedHandler.java/ApiAuthenticationEntryPoint.java/CatalogJwtAuthenticationConverter.java.
- pom.xml: chỉ bỏ dependency resource-server và security-test.
- Tests: support/BundleCoreTestSupport.java; BundleCoreConfigurationIT, BundleCoreBoundaryTest; BundleAdminServiceIT, BundleCoreHttpIT, BundleConcurrencyIT, BundleDeletionIT, BundleSkuControllerIT, AuditServiceIT. Bỏ BundleCoreSecurityIT riêng của UC13.
- Docs: spec, plan, phase1 code guide, runbook và handoff mới theo revision.
- Không sửa application YAML, password/inventory legacy, migration SQL/runner hoặc archive.

## 5. Dữ liệu truy vết không cần Audit

Dùng field hiện có: Product.createdAt/updatedAt/status; BundleConfiguration.createdAt/updatedAt/version; BundleItem.createdAt và ID giữ ổn định khi SKU không bị bỏ. Không cần migration mới cho các field đã có.

Các field này không tái tạo mọi lần sửa. createdBy/updatedBy/deletedBy chờ IAM; deletedAt hoặc bảng lịch sử BundleRevision/BundleRevisionItem chỉ bổ sung khi nghiệp vụ yêu cầu, qua migration và review riêng. Không tự thêm field vào mọi bảng hoặc nhận danh tính do client khai.

## 6. Verification và bàn giao

- ./mvnw.cmd -Pphase1-it verify: unit/HTTP/PostgreSQL integration/migration/concurrency/import regression.
- Kiểm CRUD và lookup không token, không CATALOG_AUTH_*; không tạo audit mới hoặc User giả.
- Kiểm rollback khi lưu child lỗi; stale409, soft-delete/no-op và giá/validation giữ đúng.
- Kiểm migration cũ tiếp tục chạy, bảng/lịch sử audit được giữ.
- git diff --check; đọc scan source/test không còn UC13-specific IAM imports.
- AC08 và phần audit AC11 chưa hoàn tất; không giữ số 65 test cũ như kết quả mới. Handoff ghi kết quả thực.

Review/hash cho revision này cần artifact mới trước Final Gate; không tự sửa review APPROVED cũ. Không stage/commit/push.

## 7. Báo cáo tiến độ — 2026-10-17

### JavaDoc và gộp service quản trị — plan 0.9.4

Người dùng yêu cầu comment tác dụng/luồng trước controller, service và DTO, đồng thời hỏi gộp service. Đợt này thêm JavaDoc tiếng Việt trong phạm vi combo; gộp BundleSkuQueryService.search vào BundleAdminService.searchSkus để controller gọi một application service. Giữ BundlePricingService cho công thức thuần và BundleDetailAssembler cho ghép response; không gom mọi trách nhiệm vào một class. Không đổi HTTP/JSON/schema/Auth/Audit hoặc tự stage/commit/push.

Scope bổ sung: xóa BundleSkuQueryService.java; sửa BundleAdminService/BundleAdminController, test support/architecture/controller để bỏ bean cũ; JavaDoc cho controller, service, assembler, mapper và toàn bộ payload Bundle*.java. Đồng bộ code guide/README/spec/plan/handoff. Kiểm chứng bằng clean verify với test HTTP CRUD/SKU, giá, transaction/version/migration cũ; không thêm test chỉ để kiểm comment.

Trạng thái: **READY_FOR_REVIEW**. Clean verify sau gộp đạt **47 unit + 38 integration = 85 PASS**, 0 failures/errors/skipped, BUILD SUCCESS ngày 2026-10-20 lúc 11:28:39 +07:00. Log `uc13-service-comments-verify.log`. JAR không còn BundleSkuQueryService/BundleSkuController, vẫn có generated mapper. Không thêm test chỉ kiểm comment; regression HTTP/lookup/concurrency/migration cũ đã đạt. Chưa stage/commit/push.

### Refactor MapStruct và khả năng đọc code — plan 0.9.3

Người dùng yêu cầu MapStruct và rà soát query validation, gộp controller/DTO. Yêu cầu trực tiếp này cho phép refactor nội bộ; không thay HTTP contract/schema/Phase 1, không tự cấp review APPROVED hoặc stage/commit/push.

- MapStruct 1.6.3 stable với Spring component/Lombok binding; mapper chỉ map field, BundleDetailAssembler tính giá/sellable. Gộp cấu hình processor compile/testCompile trong pom.xml.
- Gộp BundleSkuController vào BundleAdminController; giữ URL `/api/v1/catalog/admin/bundles` và `/api/v1/catalog/admin/bundle-skus`. Đổi query thành validateQueryParameters với tên biến rõ, giữ chặn tham số lạ/lặp.
- Tách BundleItemRequest/BundleJsonDeserializers dùng chung. Giữ create/update riêng vì expectedVersion chỉ có ở update; giữ summary/detail riêng để list không mang items/description. Không thêm kế thừa hoặc validation groups.
- Scope: pom.xml; BundleCoreMapper; BundleAdminService/BundleSkuQueryService/BundleDetailAssembler; hai controller; payload create/update/status và hai payload helper mới; test support/architecture/controller/assembler/fixtures; README/spec/code guide/handoff.
- Kiểm chứng generated mapper, summary/giá/sellable/null SKU, query lạ/lặp; full `-Pphase1-it verify` với PostgreSQL thật. HTTP shape/version/validation phải giữ nguyên.

Trạng thái: **READY_FOR_REVIEW**. Lần verify đầu và clean verify trạng thái cuối đều PASS: 47 unit + 38 integration = **85 test case**, 0 failures/errors/skipped. Log cuối `uc13-mapstruct-clean-verify.log`, BUILD SUCCESS ngày 2026-10-20 lúc 10:08:43 +07:00. Clean build sinh mapper từ đầu và loại class BundleSkuController cũ. 73 test của commit f9f8bc5 là baseline; thêm 12 test case trong refactor. Không stage/commit/push hoặc mở Final Gate.

### Chuẩn bị branch/commit Phase 1 — 2026-10-19

Người dùng trực tiếp yêu cầu tạo branch mới SCRUM-14, add/commit/push toàn bộ thay đổi Phase 1; yêu cầu này là quyết định của người dùng cho việc stage/commit cả plan và handoff hiện hành. Branch: `feature/SCRUM-14-phase-1`. Commit dự kiến theo Conventional Commits: `feat(catalog): implement SCRUM-14 phase 1 combo management`. Không thay lịch sử nhánh nguồn, không đưa log/build output/file môi trường riêng lên Git.

Trước khi đưa lên remote, đã bỏ credential/fallback credential kế thừa khỏi `application.yml`, thống nhất JDBC qua `SPRING_DATASOURCE_*`, SMTP qua `MAIL_USERNAME`/`MAIL_PASSWORD`; JPA mặc định `validate` thay vì `create`. Bổ sung `.env.example`, cập nhật `docs/configuration/spring-profiles.md` và test `SpringProfileConfigDataTest.credentialsResolveFromEnvironmentAndSchemaDefaultsToValidate`. Secret cũ đã có trên remote nhánh nguồn; chủ sở hữu vẫn cần revoke/rotate, không tự rewrite history. Không ghi giá trị credential vào tài liệu.

Thay đổi này phục vụ đóng gói Phase 1 an toàn, không thêm Auth/Audit hoặc Phase 2/3. Full verify trước commit đạt **36 unit + 37 integration = 73 test case PASS**, 0 failures/errors/skipped, BUILD SUCCESS ngày 2026-10-19 lúc 20:36:42 +07:00; log `uc13-phase1-precommit-verify.log`. Có thêm regression test cấu hình so với lần chạy 72 test trước đây. Không coi đây là technical review APPROVED.

### Đợt đối chiếu code và kiểm chứng mới — plan 0.9.2

Người dùng trực tiếp yêu cầu “check lại code so với tài liệu, implement code + test sau đó cập nhật tài liệu về tiến độ”. Đây là thẩm quyền triển khai cho đợt sửa nội bộ Phase 1 này, thay prerequisite xin lại approval trước sửa code; không ghi thành review độc lập APPROVED. Không mở Phase 2/3, không thay public contract hoặc SQL migration. Spec vẫn 0.9.1 vì chỉ sửa code đáp ứng contract đã mô tả.

Phát hiện: update/status đang kiểm stale trước khi loại combo DELETED nên trả 409 với version cũ; contract B06/B12 yêu cầu 404 cho DELETED. DELETE B11 vẫn kiểm version trước no-op tombstone. Chỉnh kiểm tồn tại/loại/trạng thái sau khi lấy lock và trước so version cho update/status; giữ semantics DELETE và khóa aggregate.

Scope bổ sung: `src/main/java/com/aquasuite/ims/module/catalog/internal/service/BundleAdminService.java`; test `BundleCoreHttpIT.java`, `BundleAdminServiceIT.java`, `BundleSkuControllerIT.java` trong các package catalog hiện có. Nếu phát hiện vấn đề khác trong core sẽ ghi trước khi sửa. Các tài liệu cập nhật tiến độ: plan này, handoff verification mới, README, code guide, ghi chú tiến độ spec và liên kết bằng chứng trong migration runbook; không sửa SQL/runner hoặc artifact lịch sử/review/archive.

Thứ tự: chạy baseline → bổ sung test hồi quy và chứng minh lỗi → sửa nội bộ → chạy đầy đủ `-Pphase1-it verify` với Docker/PostgreSQL → đối chiếu AC core và ghi số test/log/trạng thái thực. Mockito agent cấp qua argLine; PSModulePath thêm module PowerShell hệ thống để migration runner hoạt động. Không lưu credential hoặc token trong artifacts/log báo cáo.

Trạng thái đợt mới: **READY_FOR_REVIEW**. Full verify sau sửa PASS: 35 Surefire + 37 Failsafe = 72 test case, 0 failures/errors/skipped, BUILD SUCCESS ngày 2026-10-17 lúc 22:21:40 +07:00. Technical/code review và Final Gate chưa được cấp.

| Hạng mục | Tiến độ mới | Bằng chứng kiểm chứng mới | Còn lại |
|---|---|---|---|
| P1.1 — Schema/repository | Đã đối chiếu và kiểm chứng | BundleCoreMigrationIT 3 PASS; script/backfill/retry/checksum/constraint/rollback; boundary/config tests PASS | Review; chưa chạy live DB |
| P1.2 — CRUD/giá/transaction | Đã kiểm chứng; sửa lỗi ưu tiên 404 của DELETED | BundleAdminServiceIT 14 PASS, BundlePricingServiceTest 8 PASS, BundleConcurrencyIT 3 PASS | Code review |
| P1.3 — List/detail/lookup | Đã kiểm chứng; bổ sung lọc/paging/literal search | BundleCoreHttpIT 5 PASS, BundleSkuControllerIT 2 PASS | Code review |
| P1.4 — Soft delete/status | Đã sửa và kiểm chứng cả stale/current tombstone | HTTP regression PASS; BundleDeletionIT 2 PASS, BundleStatusIT 1 PASS; concurrency PASS | Code review |
| P1.5 — Verification/handoff | Full verify PASS trong một lần; thêm 12 test case so baseline | `uc13-iteration1-final-verify.log`; [implementation handoff](../handoffs/SCRUM14-UC13-phase1-verification.md) | Independent/code review và Final Gate; không tuyên bố đã được duyệt |
| Phase 2/3, Auth/Audit | Deferred, không phát sinh code tích hợp | Scope Phase 1 giữ nguyên; CRUD không ghi audit hoặc dùng danh tính giả | Plan/review riêng khi bắt đầu phase sau |

Log đợt này tại Git root: `uc13-iteration1-baseline-verify.log` (35+25 PASS), `uc13-iteration1-regression-before-fix.log` (test mới expected 404 / actual 409, EXPECTED_FAIL), `uc13-iteration1-final-verify.log` (35+37 PASS). Lệnh tái lập/môi trường và test-to-AC nằm trong handoff mới. Không cộng baseline hoặc regression vào tổng 72 test cuối.

### Mốc tiến độ trước đợt code — tài liệu 0.9.1

| Hạng mục | Trạng thái triển khai | Bằng chứng | Việc còn lại |
|---|---|---|---|
| P1.1 | Đã có trong workspace | Entity/repository/script; BundleCoreMigrationIT trong log cũ | Review hiện trạng; không khẳng định DB triển khai đã migrate |
| P1.2 | Đã có trong workspace | BundleAdminService/PricingService; service/pricing/concurrency tests trong log cũ | Technical review theo scope mới |
| P1.3 | Đã có trong workspace | BundleAdminController/BundleSkuController; HTTP/lookup tests trong log cũ | Review core response và giới hạn scope |
| P1.4 | Đã có trong workspace | BundleDeletionIT/BundleStatusIT/BundleConcurrencyIT trong log cũ | Review no-op/stale/tombstone |
| P1.5 | Có bằng chứng kiểm thử trước đây; chưa Final Gate | Handoff 0.9.0 và hai log được đối chiếu dưới đây | Review lại revision hiện hành; chạy lại suite nếu code/môi trường thay đổi hoặc reviewer yêu cầu |
| Đồng bộ tài liệu 0.9.1 | Đã cập nhật, chờ review | Spec, plan, README, code guide và handoff 0.9.1 | Review nội dung; không tự ghi APPROVED |
| P2.1/P2.2 | Chưa triển khai trong scope này | Thiết kế đầy đủ trong spec, chưa có bằng chứng nghiệm thu | Chốt quyết định, scope/provider/file/test và review trước code |
| P3.1/P3.2 | Chưa triển khai trong scope này | Thiết kế đầy đủ trong spec, chưa có bằng chứng nghiệm thu | Chốt Sales/Inventory/policy và review trước code |
| Auth/Audit tích hợp combo | Deferred có chủ đích | CRUD hiện không gọi provider; schema/module cũ được giữ | Tích hợp feature chung sau core; không chặn P1 |

**Bằng chứng lịch sử được đọc lại trong lần rà soát tài liệu:**

- `uc13-no-auth-full-verify.log`: Surefire 35/35 PASS; Failsafe 23/25 PASS, hai migration tests FAIL vì PowerShell không tìm Get-FileHash; toàn lần chạy BUILD FAILURE.
- `uc13-no-auth-migration-verify.log`: chạy lại BundleCoreMigrationIT sau sửa PSModulePath, 3/3 PASS và BUILD SUCCESS (kèm 8 pricing unit tests PASS). Không cộng các test chạy lại thành số test case mới.
- Tổng 60 test case của revision 0.9.0 có kết quả PASS qua nhiều lần chạy theo handoff; không nói full suite đã xanh trong một lần, không coi log cũ là kiểm chứng mới của revision tài liệu 0.9.1. Test chức năng trong lần sửa tài liệu này: NOT_RUN.
- Rà soát source xác nhận BundleAdminController/BundleAdminService có core CRUD, không yêu cầu Authorization hoặc gọi IAM/Audit. Đây là đối chiếu tĩnh, không thay runtime verification.

Khi cập nhật tiến độ, ghi ngày, hạng mục, trạng thái, bằng chứng và việc còn lại; không dùng phần trăm ước lượng hoặc đánh dấu cả AC PASS khi chỉ kiểm subset. Điểm vào bàn giao hiện hành: [implementation handoff plan 0.9.2](../handoffs/SCRUM14-UC13-phase1-verification.md). Theo yêu cầu ngày 2026-10-19, bốn handoff cũ và hai review 0.8.1 lỗi thời đã được xóa; chỉ giữ handoff mới nhất. Chưa có review mới; archive được giữ nguyên. Không thay tiến độ triển khai hoặc kết quả test trong lần dọn tài liệu này.

## Revision 0.9.5 - Flyway (user authorized)

Scope: Boot-managed Flyway/PostgreSQL dependencies; V1 complete legacy schema, V2 combo migration; preserve old manual SQL. Hibernate validate checks all current entity mappings in integration tests. Existing databases require explicit baseline 1/2 after schema review. No live migration or Phase 2/3/Auth/Audit runtime integration. Verification: clean phase1-it verify BUILD SUCCESS at 2026-10-20 13:29:56 +07; 47 Surefire + 41 Failsafe = 88 PASS, zero failures/errors/skipped. Evidence: flyway-verify.log (local ignored log). git diff --check passed.

## Revision 0.9.6 - Flyway only (user authorized)

Remove the PowerShell migration runner, db/manual SQL, obsolete manual runbook and test-only baseline DDL. Convert the 3 combo migration tests to Flyway V1/V2: backfill/retry/checksum, rollback and constraints. Keep all other changes. Update active documentation links and handoff. No live database changes. Verification: clean phase1-it verify BUILD SUCCESS at 2026-10-20 13:36:48 +07; 47 Surefire + 41 Failsafe = 88 PASS, zero failures/errors/skipped. Evidence: flyway-only-verify.log (ignored local log).

## Phase 2 - revision 0.10.0, 2026-10-20

User explicitly authorized implementation, selected same-price options, confirmed a single warehouse, and deferred Auth/Audit/media. This revision supersedes the earlier prohibition on starting P2 and the media/actor prerequisites for this scope. Authorization is not an independent APPROVED review or Final Gate.

Scope: extend create/update/detail with validFrom/validUntil/componentGroups. Preserve core requests: omitted dates allowed in drafts; if supplied both dates are required, from < until. Publish requires dates, until > now; future publish allowed. Public visibility is ACTIVE/PUBLISHED with UTC [from,until). Banner is not required while media is deferred. Add public list/detail/evaluation and publish/unpublish with aggregate lock/version/no-op. Options replace default SKUs without changing quantities; only whitelisted NORMAL ACTIVE SKUs at the same current sellingPrice as the default. Revalidate on save/publish and selected options on evaluation. Reject duplicate final SKU, stale versions and invalid quantities. Preview reads inventory only; missing/error -> UNKNOWN; never reserve. No multi-warehouse, Sales, cache, framework or fake Auth/media provider.

Repository BE = D:/Workspace/aquasuite-be. Only these files are in the P2 scope (all Java paths under src/main/java/com/aquasuite/ims/module):
- catalog/internal/entity/BundleConfiguration.java; new BundleComponentGroup.java.
- catalog/internal/payload/BundleCoreCreateRequest.java, BundleCoreUpdateRequest.java, BundleCoreDetail.java; new BundleComponentGroupRequest.java, BundleVersionRequest.java, BundleEvaluationRequest.java, BundleEvaluation.java.
- catalog/internal/mapper/BundleCoreMapper.java; service/BundleAdminService.java; controller/BundleAdminController.java; new service/BundleStorefrontService.java, controller/BundleStorefrontController.java.
- new inventory/api/InventoryAvailabilityApi.java; inventory/internal/service/impl/InventoryServiceImpl.java reuses existing batch reader. REQUIRES_NEW/read-only isolates Inventory query failures from the Catalog transaction. Legacy facade and reservation logic remain unchanged.
- new src/main/resources/db/migration/V3__SCRUM14_phase2_combo_options.sql. Keep V1/V2 unchanged. Groups stored separately, whitelist UUID[]; no JSON specifications or extra repository.
- src/test/java/com/aquasuite/ims/support/BundleCoreTestSupport.java, FlywayMigrationIT.java; architecture/BundleCoreBoundaryTest.java; module/catalog/internal/migration/BundleCoreMigrationIT.java (pin historical V2 tests); module/catalog/internal/service/BundleCoreHttpIT.java (unknown-field regression now uses a field that remains unknown); new module/catalog/internal/service/BundlePhase2IT.java, BundleStorefrontServiceTest.java.
- docs/specs/SCRUM14-UC13-product-combos.md, docs/README.md, docs/migrations/flyway.md; this plan; new .agent/handoffs/SCRUM14-UC13-phase2-verification.md. Preserve archive and Phase 1 handoff.

Sequence: data/validation -> lifecycle -> public/filter/preview/Inventory -> regression/full verify -> handoff. Reuse pricing/MapStruct/assembler/batched catalog loading; collection batch size 100. Tests cover same-price drift, whitelist/duplicates/inactive, rollback, stale/no-op, time boundaries, filter before paging, missing/negative/outage stock, quantity/price multiplication and no inventory writes. Commands: ./mvnw.cmd -Pphase1-it verify; git diff --check. Do not declare READY_FOR_REVIEW until verification completes. Security/media/audit/deployment and Sales AC remain unverified/deferred.


Phase 2 result (2026-10-20 15:05:17 +07): `./mvnw.cmd clean -Pphase1-it verify` BUILD SUCCESS, 56 Surefire + 55 Failsafe = 111 PASS, zero failures/errors/skipped. New coverage: BundleStorefrontServiceTest 9 cases and BundlePhase2IT 14 cases. Includes actual Inventory SQL failure isolation, publish/update concurrency, group-write rollback and unchanged reserved quantity. Evidence: ignored local `uc13-phase2-clean-verify.log`, target XML reports, [Phase 2 handoff](../handoffs/SCRUM14-UC13-phase2-verification.md). First run exposed a historical HTTP assertion rejecting componentGroups; it now verifies a genuinely unknown field. V1/V2 unchanged during this task. No stage/commit/push, deployment or Final Gate.


## Auth branch integration 0.11.0 - user authorized

Merge origin/SCRUM-1-user-login-and-authentication e365cad into current f9f8bc5 plus preserved uncommitted Phase 1/2. User chose incoming Auth/User UUID model and JWT required for all APIs except the incoming login/refresh allowlist. User explicitly authorized continuation after disclosure of the old hardcoded credential initializer; remove it, do not rotate external accounts or rewrite history. Backup: temporary worktree ZIP plus retained stash; no commit/push.

Scope: resolve incoming POM/YAML/env/docs conflicts; preserve MapStruct, Flyway and combo implementation. Keep V1/V2/V3 existing checksums; integrate incoming IAM DDL as V4 with a legacy-user UUID mapping/archive and convert scalar actor IDs, then V5 for password-recovery state. Preserve old account/profile/hash data without granting guessed roles. Adapt legacy password feature to the new User/repository and a separate recovery table; replace mock identity/facade with JWT UUID API. Convert inventory/audit actor contracts/entities and related fixtures to UUID. Remove duplicate User/repository/initializer/password encoder/mock SecurityUtils. Keep incoming security policy, no new allowlist for catalog.

Tests: retain incoming authentication/Redis/JWT/persistence tests, scope IAM table assertions to IAM tables and update migration-version assertions; adapt core fixture to UUID and isolate internal CRUD tests from HTTP filters. Add full-application real-JWT integration coverage for 401/malformed/valid/refresh/revoked tokens on combo and inventory. Add populated V3->V5 migration preservation checks. Run clean full verify with PostgreSQL/Redis containers, diff/conflict/secret checks. Files span incoming Auth changes plus iam legacy service/API/recovery code, inventory/audit UUID types, V4/V5, configuration, related tests, spec/plan and merge handoff. No archive edits or Final Gate claimed.

Integration detail: password recovery remains functional on the UUID account, with OTP/rate-limit state in its own table. Reset does not reactivate an INACTIVE/LOCKED account; status remains owned by the incoming authentication policy. Existing repository governance is preserved; incoming local-only .agent policy is not adopted.


Auth merge result: `./mvnw.cmd -Pphase1-it verify` BUILD SUCCESS, exit 0, 2026-10-20 17:56:28 +07. 186 Surefire + 65 Failsafe = 251 PASS, zero failures/errors/skipped, independently summed from XML reports. Prior clean build removed the duplicate/deleted legacy classes; final rerun corrected only integration fixture cleanup. Real application JWT tests 7/7 and populated upgrade tests 3/3 PASS. V1/V2/V3 verified against the pre-merge backup; Phase 1 handoff unchanged. All code conflicts resolved; MERGE_HEAD=e365cad, HEAD=f9f8bc5; awaiting human merge commit. Code staged, governance/artifacts excluded. Retained safety stash and worktree ZIP. No shared/live database migration or push. Handoff: [authentication-merge-verification](../handoffs/authentication-merge-verification.md).
