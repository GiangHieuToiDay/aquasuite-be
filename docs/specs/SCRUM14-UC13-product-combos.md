# SCRUM14 / UC-13 — View and Manage Product Combos

> Revision 0.13.0 (2026-10-21): Người dùng yêu cầu bỏ module Java audit chưa có caller nghiệp vụ. Không còn AuditApi/AuditService/entity/repository trong source. Giữ bảng audit_entries, dữ liệu lịch sử và toàn bộ migration V1-V7. Các mô tả provider Audit bên dưới chỉ còn là thiết kế lịch sử/tương lai, không thuộc chức năng hiện tại; hợp đồng response 0.12.0 và nghiệp vụ combo giữ nguyên.

> Revision 0.12.0, theo chỉ đạo người dùng ngày 2026-10-21: list/get/create/update/status/publish/unpublish dùng chung BundleResponse đầy đủ; DELETE trả HTTP 200 ApiResponse<Void>, message thành công và bỏ data. Quy định này thay thế mô tả summary/detail/mutation response trong các phần lịch sử và thiết kế cũ bên dưới. Request, validation, transaction/version, xóa mềm, evaluation và event giữ nguyên. DELETE không trả version mới; retry với version trước xóa vẫn 409, current-version no-op vẫn giữ nguyên. JSON danh sách lớn hơn do bao gồm items/mô tả/lịch/nhóm.

## Current implementation scope and verification

This section consolidates the two scoped plans removed at the user's request on 2026-10-21. Revision remains 0.13.0: this documentation update changes no contract or source code.

### Combo responses (0.12.0)

- `BundleResponse` replaces BundleCoreDetail, BundleCoreSummary and BundleCoreMutationResult, preserving all former detail fields, nested Item, dates and componentGroups.
- Lists return `ApiResponse<BundlePage<BundleResponse>>`; GET/create/update/status/publish/unpublish return `ApiResponse<BundleResponse>`. Creation retains HTTP 201 and Location.
- DELETE returns HTTP 200 `ApiResponse<Void>` with a success message and no data/version. Clients remove the row after success; retrying with the pre-delete version returns 409, and current-version no-op retries do not increment version.
- Controllers, services, assembler, MapStruct and tests were updated; summary/mutation mapping and the unused storefront mapper dependency were removed.
- Request DTOs, validation, transaction/locking/version rules, evaluations, events, Inventory and the empty catalog/api directory were preserved.
- Historical verification: clean verify PASS, 205 Surefire + 72 Failsafe = 277 tests, zero failures/errors/skipped.

### Java audit removal (0.13.0)

- Removed AuditApi, AuditCommand, AuditEntry, AuditEntryRepository, AuditService and their two integration tests; the provider had no business caller.
- Removed audit imports/scan/registrations from BundleCoreTestSupport and AuditService from BundleCoreBoundaryTest scope. No replacement provider was added.
- Preserved V1-V7 byte-for-byte, schema, historical audit_entries data and SQL-only migration compatibility fixtures/assertions. No live database SQL was run.
- The removal preserved catalog/registration/authentication production logic, HTTP response contract 0.12.0, events, Inventory and the Bruno collection.
- Latest recorded verification (2026-10-21): `mvn -o clean -Pphase1-it verify` PASS, 205 Surefire + 70 Failsafe = 275 tests, zero failures/errors/skipped; no module.audit references in production/test Java and no migration changes.

Both changes followed direct user instructions. Independent Plan Review, Code Review and Final Gate approval are not claimed. Tests were not rerun for this documentation update; counts above preserve the verification evidence consolidated from the removed handoffs.

### Response and audit verification details

- Response verification also passed `mvn -o -DskipTests compile`. Full runs used isolated Docker Testcontainers databases; XML reports were generated in target/surefire-reports and target/failsafe-reports.
- HTTP tests verify complete list/status payloads and absence of DELETE data. Deletion tests inspect persisted tombstones and no-op versions; version, rollback, concurrent-write and multi-bundle isolation coverage was retained.
- PostgreSQL rounds timestamps to microseconds: updatedAt assertions allow one microsecond against an immediate mutation response, while no-op comparisons use persisted responses. The complete clean rerun passed after correcting this assertion.
- Removed DTO/mapper references were absent from active Java; audit removal also removed its compiled provider output through clean verification. The reduction from 277 to 275 tests is exactly the two deleted AuditServiceIT cases.
- Frontend clients outside this repository were not modified or verified. List payloads are larger, DELETE clients must stop reading data/version, and pre-delete-version retries still return 409.
- Audit removal added no stub, feature flag, dependency or replacement audit provider. Existing historical rows remain available; fresh installs still create/upgrade audit_entries through the preserved migrations.

### Authentication integration evidence (0.11.0, historical)

Source: `origin/SCRUM-1-user-login-and-authentication` at `e365cad`; original target: `feature/SCRUM-14-phase-1` at `f9f8bc5` plus preserved Phase 1/2 changes. The following records the integration as verified on 2026-10-20, not today's Git merge state. Old statements that the merge was staged/uncommitted describe that verification snapshot only.

- Reused the incoming JWT decoder, current-authority converter, login/refresh/logout and Redis rotation/revocation/session implementation. Removed duplicate legacy User/repository/encoder, mock SecurityUtils/fallback identity and the hardcoded credential initializer. Development seeding is environment-driven and disabled by default.
- User, Role and Permission identities use UUID. Verified JWT subjects identify password changes and inventory actors. IAM's public current-user contract remains in iam/api without entity exposure or cross-module JPA relationships. Audit actor IDs were also migrated at this stage; its Java provider was later removed in 0.13.0.
- Password change/recovery use the same UUID account/hash, with recovery hashes, expiry and rate-limit state in a separate table. Reset does not reactivate INACTIVE/LOCKED accounts.
- V1/V2/V3 retained their original bytes. V4 integrates standalone IAM DDL forward without duplicate version 1, archives full legacy rows in iam_legacy_users and retains deterministic numeric-to-UUID mappings in iam_legacy_user_ids, including orphan actor IDs. Missing email/password identities remain locked with original values archived; case-insensitive identity collisions abort atomically. No numeric role IDs are guessed into new grants. V5 preserves password recovery state.
- At integration time, only POST login/refresh were unauthenticated. All other route families, including storefront, admin, evaluations, inventory and password recovery, required an access JWT, current ACTIVE user and live Redis session. Refresh tokens cannot authorize business APIs. Registration later added its explicit public endpoints; consult the current registration spec and AuthSecurityConfig for the current allowlist.
- Existing combo requests, pricing, versioning, options, lifecycle, Inventory batch reads, ApiResponse and business errors were preserved. This merge did not introduce a Catalog permission matrix; finer business permissions, business audit, media and Sales remained separate scope.

Recorded verification: `./mvnw.cmd -Pphase1-it verify`, exit 0, BUILD SUCCESS at 2026-10-20 17:56:28 +07 (2m57s); 186 Surefire + 65 Failsafe = 251 tests, zero failures/errors/skipped, independently summed from XML. Surefire includes database/Redis integration cases and is not a pure unit-test count.

| Coverage | Recorded evidence |
|---|---|
| AuthenticationMergeIT | 7/7 complete-application cases: missing JWT across route families, valid access to combo/admin/evaluation, rejection of refresh/malformed tokens, logout revocation, inventory UUID actor, same-account password change, disabled-user rejection |
| AuthenticationUpgradeIT | 3/3 cases: populated V3-to-V5 account/profile/hash/recovery/audit/inventory/combo preservation and retry, atomic rollback on identity conflict, incomplete-account locking |
| Existing regressions | Phase 1/2 plus incoming Auth/Redis/JWT/roles/seeding/repositories/password suites retained; original sensitive-data and identity constraints retained |
| Source/build checks | Clean removed stale legacy classes; one User entity, no old initializer/mock identity, no duplicate direct POM dependencies or unresolved conflicts; test-only core configuration excluded from production scanning |
| Test adjustments | IAM foundation assertions adapted to the merged schema; source fingerprints compare canonical LF; complete-app fixtures clean product_variants; missing-secret startup assertion isolates JwtTokenService |

Reports were generated in target/surefire-reports and target/failsafe-reports; the local auth-merge-final-verify.log was ignored. Existing V1 trailing spaces were intentionally preserved to avoid migration checksum changes. Full-application JWT coverage did not disable security filters or skip tests.

No live/shared migration, deployment, external credential rotation or independent review/Final Gate was performed in that verification. Rehearse and back up before migration; standalone Auth databases have a different V1 and require reviewed onboarding rather than automatic baseline/repair. Legacy created_at is interpreted as UTC; archived raw rows support reconciliation, and new role grants require the IAM ownership process. JWT, database, Redis and SMTP credentials come from deployment environment; prior credential revocation/history remediation remains human-owned.

The historical merge retained a safety stash named `safety before authentication merge 2026-10-20` and a local temporary worktree ZIP. Their current availability is not verified; do not blindly reapply old backups onto integrated code. Incoming approval artifacts do not approve this integrated revision. Implementation was directly user-authorized; no independent/hash-matched 0.11.0 Plan Review approval is claimed.

## Metadata and Governance

| Thông tin | Giá trị |
|---|---|
| Work item | `SCRUM14` (theo yêu cầu người dùng; chưa đối chiếu hệ thống quản lý ticket). |
| Feature ID / Use Case | `FE-03 / UC-13`. |
| Feature Name | View and Manage Product Combos — Xem và quản lý combo sản phẩm |
| Version | `0.13.0`. |
| Status | Java audit removal authorized by user; clean verify PASS (205 Surefire + 70 Failsafe = 275 tests, 2026-10-21); independent technical/code review PENDING. Response 0.12.0 verification is historical. |
| Owner / Author / Reviewer | QuangNB; cần người phụ trách nghiệp vụ và kỹ thuật review. |
| Owning Module | `catalog`. |
| Repository Scope | `BE (aquasuite-be)`. |
| Target Package | `com.aquasuite.ims.module.catalog`, theo quyết định của người dùng. |
| Risk Level | `HIGH` vì liên quan giá ưu đãi, tồn kho và dữ liệu chuyển sang đơn hàng. |
| Implementation guide | [Plan 0.9.4 và tiến độ SCRUM14](../../.agent/plans/SCRUM14-UC13.md); [handoff kiểm chứng core](../../.agent/handoffs/SCRUM14-UC13-phase1-verification.md). |

### Nguồn và cách đọc

| Nguồn | Vị trí | Vai trò |
|---|---|---|
| `D:\AQS_Report-3_Software Requirement Specification_v1.0_29092026.pdf` | Trang PDF 12, mục I.4.2; trang 52-53, mục II.3.6 | Phạm vi và luồng UC-13 |
| Cùng Report 3 | Mục I.4.4, trang PDF 26-27; appendix MSG44 | Đối chiếu quyền và thông báo thành công |
| `D:\Database.pdf` | Trang PDF 5-7, bảng 8-13 | Products, variants, images, bundle_items; phần mô tả chi tiết cập nhật |
| Cùng Database.pdf | Trang PDF 9; trang 11, 13-14 | Tồn khả dụng và dữ liệu báo giá/giỏ hàng/đơn hàng liên quan |
| Mã nguồn repository | Các đường dẫn ở bảng hiện trạng bên dưới | Bằng chứng cấu trúc và thành phần đang có; chưa chứng minh chạy đúng |
| [API conventions](../contracts/api-conventions.md), [module contracts](../architecture/module-contracts.md), [workflow](../workflow/feature-delivery.md) | Quy ước repository | Envelope, ranh giới và review gates |

Số trang là thứ tự trang trong PDF, tính từ 1. Nội dung tuyển dụng và placeholder ở phần khác của SRS không dùng cho UC này. Không sao chép PDF hay thông tin liên hệ cá nhân vào bộ tài liệu.

**Phân biệt mức độ xác nhận:** `SRS` là yêu cầu được trích từ UC-13; `CURRENT` là điều quan sát từ code; `PROPOSED` là thiết kế để review; `TBD` là quyết định chưa chốt. Các endpoint, mã permission, schema mở rộng và chữ ký Java trong tài liệu này đều là **PROPOSED**, chưa phải contract đã duyệt.

### Hiện trạng và phần thiếu

Các đường dẫn dưới đây tương đối từ `src/main/java/com/aquasuite/ims/`:

| Thành phần | CURRENT | Cần làm cho UC-13 |
|---|---|---|
| `module/catalog/internal/entity/Product.java` | Có mã, tên và trạng thái; ProductType NORMAL/BUNDLE | Core dùng lại Product BUNDLE, không tạo SKU hoặc tồn kho combo |
| `module/catalog/internal/entity/BundleItem.java` | Có thành phần SKU/quantity; workspace đã bổ sung validation và constraint | Core từ chối quantity không hợp lệ, không tự đổi thành 1 |
| `module/catalog/internal/entity/BundleConfiguration.java` | Có giá gói, publication, timestamp và @Version | Core tạo DRAFT; field thời hạn/stock policy không đồng nghĩa đã triển khai publish/kiểm tồn |
| `module/catalog/internal/entity/ProductVariant.java` | Có SKU, `sellingPrice`, trạng thái, màu/kích thước | Tra cứu SKU cấu thành; chưa có cấu trúc nhóm lựa chọn combo |
| `module/catalog/internal/entity/ProductImage.java` | URL, primary flag, thứ tự ảnh, ID product/variant | Gắn banner combo và xác minh quyền sở hữu asset |
| `module/catalog/internal/repository/ProductRepository.java`, `ProductVariantRepository.java`, `BundleConfigurationRepository.java`, `BundleItemRepository.java` | Có truy vấn combo/SKU, lọc/phân trang và khóa aggregate | Dùng cho core; không đọc repository module khác |
| `module/inventory/InventoryFacade.java` | Có `getStockSummaries(Collection<UUID>)`, kiểm tra/giữ/nhả tồn | Đọc tồn qua contract được duyệt; không import entity/repository inventory |
| `module/iam/IamFacade.java` và implementation | Có facade nhưng implementation còn fallback người dùng giả lập | Không dùng fallback để cấp quyền quản trị combo |
| `module/catalog/internal/controller/BundleAdminController.java` | Gộp CRUD và SKU lookup; URL /bundles và /bundle-skus giữ nguyên | Chặn query lạ/lặp, không yêu cầu token/actor/permission |
| `module/catalog/internal/mapper/BundleCoreMapper.java`, `service/BundleDetailAssembler.java` | MapStruct chuyển field; assembler tính giá/sellable | Refactor nội bộ plan 0.9.3, không đổi JSON/HTTP/schema |
| `module/catalog/internal/service/BundleAdminService.java`, `BundlePricingService.java` | Một service điều phối CRUD/SKU lookup; pricing giữ công thức dùng chung; JavaDoc mô tả tác dụng/luồng | Core không gọi IAM/Audit/Inventory/Sales/Cloudinary; HTTP contract giữ nguyên sau gộp |

Hiện trạng được đối chiếu workspace ngày 2026-10-17, bao gồm thay đổi chưa commit; không khẳng định đã deploy hoặc kiểm live DB. Đợt tài liệu 0.9.1 không chạy test; đợt code plan 0.9.2 sau đó đã chạy full verify 72 test case PASS, xem handoff kiểm chứng core ở Metadata. Không khẳng định UC-13 đã hoàn thành chỉ vì core có mã nguồn hoặc đạt subset test.

Package đã được chốt là `com.aquasuite.ims.module`, không dùng `com.aquasuite.be`. Baseline kiến trúc chung đã được đính chính về sự tồn tại của module nghiệp vụ; không thay kiến trúc đích hoặc di chuyển code. Module mới chia sẻ qua `api/`; facade/DTO/event legacy ngoài `api/` là điểm chuyển tiếp cần plan riêng hoặc scope được duyệt.

### Quy chuẩn áp dụng và baseline

| Nguồn quy định | Cách áp dụng trong spec |
|---|---|
| [Template](../templates/feature-spec.md) | Metadata và đủ tám mục chính; HIGH nên mô tả transaction, concurrency, phục hồi và AC-to-Test |
| [Kiến trúc](../architecture/Architecture-Aquasuite.md) | Module sở hữu dữ liệu; chỉ trao đổi qua api; không JPA relationship chéo module; kiểm tra bằng ArchUnit |
| [Module contracts](../architecture/module-contracts.md) | Mỗi Java API đủ caller/provider/input/output/errors/auth/transaction/compatibility; event không dùng ghi rõ N/A |
| [API conventions](../contracts/api-conventions.md) | ApiResponse, BaseErrorCode, validation, HTTP method/status; không trả entity |
| [Spring profiles](../configuration/spring-profiles.md) | Giữ common/dev/test/prod, không bật profile mặc định; marker chỉ chẩn đoán; env/Secret Manager cấp cấu hình |
| [Migration Boot](../migrations/spring-boot-3.5.md) | Java 21, Boot 3.5.16, Framework 6.2.x, Security 6.5.x, Jackson 2, JUnit 5 theo BOM; không thêm test starter dành riêng Boot 4 |
| [Workflow](../workflow/feature-delivery.md) | Spec review trước plan activation; independent plan review/hash; handoff trước code review; chưa có approval nào được tự cấp |

Baseline được đối chiếu workspace ngày 2026-10-17. Quyết định package tiếp tục là `com.aquasuite.ims.module`; `com.aquasuite.be.features` trong template/contract là kiến trúc đích. Nhận định cũ “chưa có entity” đã được đính chính ở baseline kiến trúc và README; bảng CURRENT phản ánh code quan sát được. Tài liệu migration không phải chứng nhận mọi test UC13 đã chạy. Không sửa governance hoặc lịch sử kiểm chứng để hợp thức hóa spec.

## 1. Business Context and Objectives

### Problem Statement

Cửa hàng cần bán các SKU thành một bộ có giá ưu đãi. Nếu mỗi kênh tự tính thành phần, giá và tồn, khách có thể nhận giá khác nhau hoặc mua bộ thiếu hàng.

### Business Goals & Scope

Store Owner cấu hình combo thiết bị phòng tắm, gồm các SKU và số lượng, ảnh quảng bá, giá ưu đãi và thời hạn. Sales Staff, Customer, Guest có thể tiếp cận combo theo quyền được chốt; giá và thành phần được dùng nhất quán khi chuyển sang POS, báo giá hoặc giỏ hàng.

### Phạm vi yêu cầu đầy đủ của UC-13

| ID | Yêu cầu SRS | Nguồn |
|---|---|---|
| R01 | Danh sách combo: tên, thumbnail, số mặt hàng liên kết, tổng giá lẻ, giá gói, phần trăm giảm | Normal flow 1-2 |
| R02 | Tạo combo: tên, mô tả, banner Cloudinary, SKU và số lượng | Normal flow 3-6 |
| R03 | Tính tổng giá lẻ, đặt giá ưu đãi và hiển thị tiết kiệm | Normal flow 7-8 |
| R04 | Cấu hình thời hạn và quy tắc kiểm tra/giữ tồn | Normal flow 9 |
| R05 | Publish, lưu định nghĩa/thành phần và cung cấp cho storefront/POS; MSG44 | Normal flow 10-11, postconditions |
| R06 | Khách xem ảnh và chọn option SKU, ví dụ màu hoàn thiện | Alternative flow: Customer, 1-3 |
| R07 | Thêm toàn bộ SKU vào giỏ với giảm giá combo | Alternative flow: Customer, 4-5 |
| R08 | Thiếu SKU bắt buộc: hiển thị thiếu một phần, chỉ rõ SKU, đề xuất thay thế hoặc đặt trước | Alternative flow: depleted component |
| R09 | Giá combo được công nhận ở POS, báo giá và checkout | Postcondition 2 |

### Phạm vi đặc tả

Tài liệu này đặc tả đầy đủ UC-13: quản lý combo, lựa chọn biến thể do Owner cho phép, đánh giá giá/tồn, thêm giỏ, sử dụng tại POS/báo giá/checkout, thay thế SKU thiếu, preorder và giữ/nhả tồn. Phân bổ nghiệp vụ theo ba phase nằm trong spec; [implementation plan](../../.agent/plans/SCRUM14-UC13.md) dẫn từ đó để xác định đầu việc, tiến độ và kiểm chứng.

### Phạm vi nghiệp vụ và phạm vi đợt code

Spec này giữ toàn bộ R01-R09, kể cả ảnh, public browse, thời hạn/publish, kiểm tồn, option, thay thế, preorder và tích hợp Sales. Bổ sung quản trị xóa mềm/bật tắt theo yêu cầu người dùng ngày 2026-10-17. Plan chỉ kích hoạt phần được chọn; việc chưa code một API không xóa API đó khỏi đặc tả.

| Phần đặc tả | Contract | Quan hệ với đợt core |
|---|---|---|
| Quản trị nội bộ | B03-B06, B11-B12, C01; Core DTO bên dưới | Phần chuẩn bị triển khai trước; tạo/sửa/xóa mềm/bật tắt, giá và lookup |
| Đăng bán và trải nghiệm khách | B01-B02/B07-B09, C02-C03 | Vẫn phải triển khai để đáp ứng UC13; chưa thuộc đợt core |
| Tùy chọn và giao dịch | componentGroups, B10, S01-S09 và Java API tương ứng | Giữ đầy đủ nghiệp vụ; chỉ triển khai theo gate sau |

### Phân bổ phạm vi theo ba phase

| Phase | Nghiệp vụ / luồng | Contract và yêu cầu | Điều kiện nghiệm thu |
|---|---|---|---|
| **1 — Core quản trị, ưu tiên Iteration 1** | Danh sách/chi tiết, tìm SKU, tạo/sửa, xóa mềm, bật/tắt, tính giá và kiểm dữ liệu | B03-B06 **Core DTO**, B11-B12, C01; phần quản trị R01-R03 và bổ sung xóa/status | Dữ liệu đúng, transaction/rollback, version và response chuẩn; **không Auth/Audit**, không phụ thuộc ảnh, tồn, Sales hoặc quyết định Phase 3 |
| 2 — Đăng bán và lựa chọn | Banner, thời hạn, publish/unpublish, khách xem, preview giá/tồn, chọn option được phép | B01-B02, B07-B09, C02-C03; mở rộng B03-B06 với media/dates/componentGroups; R01-R06 và phần hiển thị thiếu SKU của R08 | Chốt public visibility, media, giá option và Inventory read; tích hợp Auth/Audit chung theo scope riêng khi sẵn sàng |
| 3 — Giao dịch và thiếu hàng | Giỏ hàng/POS/báo giá/checkout; snapshot, thay thế, preorder, giữ/nhả tồn | B10, S01-S09 và Java API liên module; R07-R09 và phần xử lý thiếu hàng của R08 | Chốt policy giá/hiệu lực/ownership/transaction/reservation; kiểm chứng tích hợp thực |

Không dựng JWT verifier, hạ tầng audit, outbox, saga hoặc reservation trong Phase 1. Auth/Audit là tích hợp tương lai, không phải prerequisite của CRUD Iteration 1; mốc đưa vào Phase 2/3 phụ thuộc feature chung và plan được duyệt. Quyết định chưa chốt ở phase sau không chặn nghiệm thu core. Hoàn thành Phase 1 chưa phải hoàn thành UC-13.

### Luồng nghiệp vụ chính và luồng thay thế

Actor dưới đây là vai trò nghiệp vụ; Phase 1 chưa xác minh vai trò bằng Auth.

| Luồng | Tiền điều kiện / các bước | Kết quả và phase |
|---|---|---|
| Quản lý combo — luồng chính | Người quản lý mở danh sách → tìm SKU → nhập mã/tên/thành phần/số lượng → BE tính tổng lẻ → nhập giá gói → lưu → xem lại hoặc sửa với version hiện tại | Phase 1 lưu Product BUNDLE + config DRAFT + items nguyên tử, tính giá tiết kiệm phía BE. Phase 2 mới bổ sung banner/thời hạn/chính sách tồn rồi publish, trả MSG44 và đưa lên kênh bán |
| Quản trị bổ sung | Tìm/xem chi tiết → bật/tắt hoặc xác nhận xóa → gửi expectedVersion | Phase 1 đổi ACTIVE/INACTIVE hoặc xóa mềm DELETED; giữ mã/thành phần, không tự publish hoặc tác động tồn |
| Dữ liệu sai / SKU không hợp lệ | Mã trùng, thiếu field, giá sai, quantity sai, SKU trùng/inactive/nesting → từ chối và trả lỗi tương ứng | Phase 1 không lưu dở dang; người quản lý sửa đầu vào rồi gửi lại |
| Xung đột / không tồn tại | Sửa/status/delete với version cũ → 409; combo không tồn tại/đã xóa → xử lý theo contract từng API | Phase 1 đọc lại version trước khi thay đổi; no-op status/delete chỉ với version hiện tại, không tự ghi đè |
| Khách xem/chọn option — luồng thay thế UC-13 | Mở combo công khai/còn hiệu lực → xem ảnh/thành phần → chọn SKU trong nhóm Owner cho phép → đánh giá lại giá/tồn | Phase 2 chỉ preview, chưa tạo giỏ/giữ hàng; option ngoài whitelist bị từ chối |
| Mua combo | Khách/Staff chấp nhận cấu hình và giá → thêm toàn bộ SKU × số bộ vào giỏ/báo giá/POS → kiểm tra lại khi checkout/xác nhận | Phase 3 Sales lưu grouping/snapshot và giá được chấp nhận, không tin giá client |
| Thiếu SKU — luồng thay thế UC-13 | Chỉ rõ thành phần thiếu → đề nghị thay thế hợp lệ hoặc preorder → khách xác nhận theo policy đã chốt | Phase 2 hiển thị; Phase 3 xử lý thay thế/preorder. Không tự đổi SKU, giữ một phần hoặc xác nhận đơn khi chưa đủ điều kiện |
| Dependency/ghi DB lỗi | Không đọc được tồn → UNKNOWN khi preview; lỗi ghi aggregate → rollback; lỗi media không publish dở dang | Phase 1 bảo đảm rollback DB; Phase 2/3 xử lý dependency theo contract từng thao tác |

### Ranh giới triển khai đề xuất

- **Catalog sở hữu** định nghĩa combo, cấu hình thành phần/lựa chọn, ảnh, giá và hiệu lực; cung cấp kết quả đánh giá combo cho consumer.
- **Inventory sở hữu** tồn SKU và thay đổi tồn. Xem/publish combo không tự tạo tồn kho combo hoặc trừ tồn.
- **Sales sở hữu** giỏ hàng, báo giá, đơn hàng và snapshot giá đã chấp nhận. R07/R09 cần tích hợp với sales, không được đánh dấu hoàn tất bằng một API catalog đơn lẻ.
- **IAM sở hữu** danh tính/quyền; **audit** xử lý lịch sử thay đổi nhạy cảm; tích hợp media chịu trách nhiệm Cloudinary.

### Out of Scope

Ngoài phạm vi: viết frontend trong repo khác; xây dựng toàn bộ checkout/POS; thu tiền; xuất kho; tự migration toàn bộ package; sửa các UC khác. R01-R09 đều thuộc nghiệm thu đầy đủ UC-13.

## 2. Actors, Permissions, and Security Model

### Actor and Authorization Matrix — mục tiêu tích hợp sau Phase 1

| Actor | Hành động | Quyền đề xuất | Trạng thái |
|---|---|---|---|
| Store Owner | Tạo, sửa, publish/unpublish, xem quản trị | `catalog:bundle:manage` | SRS xác định quản lý; tên permission chưa duyệt |
| Sales Staff | Xem và dùng combo trong báo giá/POS | `catalog:bundle:read` cùng quyền sales tương ứng | Không suy ra có quyền sửa combo |
| Customer / Guest | Xem combo công khai, chọn option; thêm vào giỏ theo quy tắc giỏ hàng | Public read cho combo đang hiệu lực; quyền giỏ hàng do sales | Cần chốt D01 |
| Cloudinary | Lưu ảnh quảng bá | Credential server từ môi trường | Không phải actor được sửa catalog |

**D01 - Quyền xem cần xác nhận:** UC-13 trang 52 nêu Guest/Customer là primary actors; permission matrix trang 27 có dòng `Product bundle / View / Suggest` riêng với phạm vi quyền hẹp hơn dòng `Product (public)`. Cần xác nhận dòng bundle là chức năng nội bộ hay cũng chi phối storefront. Không tự chọn một cách hiểu để mở endpoint public.

- Response công khai không chứa `costPrice`, dữ liệu supplier hoặc thông tin tài khoản nội bộ.
- Khi tích hợp Auth ở phase sau, write API kiểm tra quyền phía server, không tin role/owner từ request. Phase 1 không kiểm quyền.
- Tách schema request/response khỏi entity; không cho client tự ghi `productType`, discount tính toán hoặc audit actor.
- Không ghi token, credential Cloudinary hay dữ liệu nhạy cảm vào log/spec. Xác minh asset được phép dùng; không tải URL tùy ý từ client.

| Actor | Role code thiết kế | Identity source / context | Permission và kiểm tra |
|---|---|---|---|
| Store Owner | OWNER, không suy ra từ roleId số hiện có | JWT đã kiểm chữ ký + User ACTIVE | catalog:bundle:manage; quyền do issuer tin cậy cấp |
| Sales Staff | SALES_STAFF | JWT + User ACTIVE | catalog:bundle:read và quyền giỏ/báo giá/đơn của Sales; không có quyền quản trị combo |
| Customer | CUSTOMER | JWT hoặc context khách đã được IAM xác minh | Public browse; Sales kiểm ownership trước mọi write |
| Guest | ANONYMOUS | Không token khi browse; phiên giỏ server khi giao dịch | Không admin; quyền phiên giỏ phải được Sales duyệt trước tích hợp |
| System | SYSTEM | Spring context/job, không HTTP identity giả | Cleanup/audit nội bộ; không dùng user 1 fallback |

Các tên role là ngữ nghĩa thiết kế, không khẳng định có bảng Role hoặc mapping production. D01 vẫn cần xác nhận nghiệp vụ.

### Data Protection and Sensitive Fields

Giá vốn, supplier, JWT, password và Cloudinary credential không xuất hiện trong DTO công khai/audit payload. Log lỗi dùng aggregate/asset ID, không dump request Authorization hoặc toàn bộ environment. User ID trong hệ thống hiện tại là Long; UUID dùng cho product/variant/combo.

### Phạm vi IAM hiện tại và tích hợp tương lai

Theo yêu cầu người dùng ngày 2026-10-17, core quản lý combo hiện không kiểm actor, JWT hoặc permission và không yêu cầu cấu hình CATALOG_AUTH_*. IAM/xác thực/phân quyền sẽ do feature khác cung cấp; Catalog chỉ tích hợp lại hợp đồng chung khi feature đó sẵn sàng, không tự xây verifier hoặc giả lập User.

Các ma trận actor/quyền và hợp đồng IAM/Audit trong phần thiết kế đầy đủ bên dưới mô tả mục tiêu tương lai, chưa áp dụng cho core hiện tại. Endpoint quản lý hiện chấp nhận request không có Authorization. HTTP validation, quy tắc SKU/giá, transaction và expectedVersion vẫn bắt buộc.

### Dấu vết dữ liệu hiện tại và lịch sử thay đổi

| Dữ liệu | Mục đích và giới hạn |
|---|---|
| Product.createdAt/updatedAt | Mốc tạo/cập nhật dữ liệu sản phẩm chung |
| BundleConfiguration.createdAt/updatedAt | Mốc tạo/cập nhật aggregate combo, nguồn timestamp của core response |
| BundleConfiguration.version | Phát hiện client dùng dữ liệu cũ; không lưu nội dung từng phiên bản |
| Product.status=DELETED | Xóa mềm, giữ mã/ID/thành phần; không lưu thời điểm xóa riêng |
| createdBy/updatedBy/deletedBy | Deferred đến IAM; không nhận Actor ID client tự khai hoặc ghi User giả |
| deletedAt / lịch sử giá-thành phần | Có thể bổ sung bằng migration riêng nếu cần; chưa thêm trong lần sửa này |

Core không ghi Audit nghiệp vụ. Module Java Audit đã được bỏ; chỉ giữ bảng audit_entries để tương thích migration và dữ liệu lịch sử, không có bản ghi CRUD combo mới. Không sửa file migration đã phát hành hoặc xóa lịch sử cũ.

Timestamp/status/version chỉ giúp truy vết trạng thái gần nhất, không đáp ứng lịch sử before/after từng lần sửa. Nếu nghiệp vụ cần lịch sử đầy đủ mà không dùng module Audit, có thể thiết kế bảng BundleRevision/BundleRevisionItem riêng chứa bản sao definition theo version và ghi cùng transaction. Đây là phương án chưa triển khai, cần chốt thời hạn lưu và migration. Không nhân bản actor fields ở mọi bảng child nếu có thể lấy từ aggregate.

## 3. Detailed Business Rules and Logic

### Core Business Rules

1. **SRS:** combo gồm các SKU cụ thể và số lượng; backend tính `originalTotal = Σ(sellingPrice_i × quantity_i)`.
2. **SRS:** giá gói do Store Owner đặt. `savingAmount = originalTotal - packagePrice`; `discountPercent = savingAmount / originalTotal × 100`. Phương án xử lý giá không hợp lệ ở mục 3.1, chờ D03 phê duyệt.
3. **SRS:** ví dụ tổng `110,000,000 VND`, giá gói `95,000,000 VND`, tiết kiệm `15,000,000 VND`, hiển thị `13.6%`. Không lưu float cho tiền; `BigDecimal` và quy tắc làm tròn cần duyệt.
4. **PROPOSED:** ít nhất một SKU, số lượng nguyên dương theo `bundle_items.quantity INT`; không tự sửa 0/âm thành 1. Nếu cần vật tư lẻ theo mét, phải đổi schema và contract qua D02.
5. **PROPOSED:** cấm combo chứa chính nó hoặc SKU của product loại BUNDLE, tránh vòng lặp/nesting; SKU phải tồn tại và đủ điều kiện bán. Cách xử lý SKU trùng: reject thay vì ngầm cộng; cần duyệt D02.
6. **PROPOSED:** tổng giá lẻ khi browse dựa trên giá hiện tại; giao dịch sales lưu snapshot. Giá gói và option cần được kiểm tra lại khi checkout; không tin giá từ client (D03/D07).
7. **SRS:** thiếu SKU bắt buộc phải hiển thị thiếu một phần và chỉ rõ SKU. **PROPOSED:** với lựa chọn đã cố định, số bộ khả dụng là `max(0, min(floor(availableQty_i / quantity_i)))`. Chỉ là kết quả đọc, không bảo đảm giữ tồn cho checkout.
8. **PROPOSED:** nếu inventory không trả dữ liệu/lỗi, trạng thái `UNKNOWN`, không báo còn hàng. Mỗi combo/selection cần được đánh giá bằng batch lookup SKU để tránh N+1.
9. **SRS:** cho phép chọn biến thể và gợi ý thay thế/đặt trước. Không tự thay SKU hoặc cam kết đặt trước nếu người dùng chưa xác nhận và quy tắc D05/D06 chưa được duyệt.
10. **FUTURE INTEGRATION:** khi tích hợp IAM/Audit, chỉnh giá/thành phần/publish ghi actor, thời gian và before/after. Phase 1 chỉ giữ timestamp/status/version, không gọi Audit. Snapshot giao dịch cũ không đổi theo Catalog.

### Ví dụ kiểm chứng số lượng, giá và tồn (minh họa, không thay ví dụ SRS)

Một bộ gồm bồn cầu 1 × 4.000.000, lavabo 1 × 2.000.000 và vòi 2 × 1.000.000 VND: tổng lẻ 8.000.000, giá gói 7.200.000, tiết kiệm 800.000 (10%). Hai bộ cần lần lượt 2/2/4 SKU và giá 14.400.000 VND. Nếu tồn khả dụng là 5/3/6 thì số bộ đọc được là min(5, 3, 3) = 3. Hai khách cùng yêu cầu 3 bộ vẫn cần reservation nguyên tử khi xác nhận; B09 không bảo đảm cả hai mua được.

### State Machine / Lifecycle

Không thêm `DRAFT` vào `ProductStatus` hiện tại một cách ngầm định. Dùng trạng thái publish riêng trong thiết kế mở rộng (D02/D04):

| From | Action | To | Điều kiện |
|---|---|---|---|
| Chưa có | Tạo định nghĩa | DRAFT | Dữ liệu và SKU hợp lệ; chưa công khai |
| DRAFT / UNPUBLISHED | Publish | PUBLISHED | Giá, ảnh, thành phần, thời hạn và quyền hợp lệ |
| PUBLISHED | Unpublish | UNPUBLISHED | Quyền quản lý; không xóa snapshot giao dịch cũ |
| DRAFT / UNPUBLISHED | Update definition | Giữ trạng thái | expectedVersion khớp; thay toàn bộ definition nguyên tử |
| PUBLISHED | Hết thời hạn | PUBLISHED, nhưng `effective=false` | Hiệu lực tính khi đọc; không cần job bắt buộc |

`Product.status` vẫn là điều kiện hoạt động của product. Public visibility đề xuất: product ACTIVE, publication PUBLISHED và thời điểm nằm trong khoảng hiệu lực. Timezone, biên đóng/mở và việc cho phép null ngày là D04. Hành vi edit combo đang publish và kiểm soát revision là D08.

### 3.1 Quy tắc giá đề xuất để reviewer quyết định

| Nội dung | Quy tắc đề xuất |
|---|---|
| Giá gói | Lớn hơn 0, không lớn hơn tổng giá lẻ của cấu hình đang đánh giá. Nếu giá lẻ giảm khiến điều kiện sai: browse vẫn hiển thị nhưng `purchasable=false`, `priceIssue=PACKAGE_EXCEEDS_RETAIL`; không hiển thị giảm giá âm, không âm thầm sửa giá gói; giao dịch trả CATALOG_BUNDLE_004 |
| Độ chính xác | BigDecimal, tiền 2 chữ số thập phân, HALF_UP; phần trăm hiển thị 1 chữ số. Kiểm tra tràn DECIMAL(15,2) sau nhân số bộ |
| Giá option | D05-P chưa chốt: (A) giá cố định do Owner chấp nhận biên lợi nhuận, (B) phụ phí từng option, hoặc (C) cấm option khác giá. Contract hiện tại chỉ minh họa A; nếu chọn B phải thêm adjustment và công thức/schema/test trước code; C phải kiểm tra điều kiện giá khi publish và evaluate. Không mặc định A. SKU cuối cùng trùng nhau bị từ chối theo proposal D02 |
| Giá thay thế | Giá đề nghị = (giá gói + tổng priceAdjustment × quantityPerBundle của dòng thay thế) × số bộ. Giá điều chỉnh do Owner cấu hình, không lấy từ client; hiển thị chênh lệch và yêu cầu khách xác nhận kể cả bằng 0 |
| Phân bổ dòng | Tỷ trọng theo giá lẻ × quantity; làm tròn xuống 2 chữ số, chia phần dư 0.01 theo phần lẻ lớn nhất, hòa thì theo ID thành phần. Tổng dòng phải bằng tổng giá gói đã chấp nhận; chia số lượng không được làm mất phần dư |
| Thuế | Snapshot lưu tổng giá và cách hiểu thuế theo contract Sales/Finance được duyệt; không tự áp thêm thuế lên giá gói. Nếu nền tảng chưa chốt quy tắc này thì chưa nghiệm thu POS/checkout |

Các response giá ở mục 5.1.3 bổ sung `purchasable: boolean` và `priceIssue: string|null`; khi không có ưu đãi hợp lệ, `savingAmount`/`discountPercent` là null. `purchasable` chỉ phản ánh điều kiện tại lần đọc, không bảo đảm tồn đã được giữ. Quy tắc này là đề xuất cụ thể cho D03/D05/D07, vẫn chờ review.

### 3.2 Quy tắc cấu hình cố định đề nghị phê duyệt

D01, D04, D06-read, D09-audit/media và phần auth/upload của D10 là thiết kế cho phase sau. Phase 1 chỉ áp dụng subset quản trị ở mục 3.3 và Core DTO; không yêu cầu issuer/token/actor/audit/media/tồn để chạy CRUD.

| Quyết định | Phương án đưa vào review | Điều kiện kiểm chứng |
|---|---|---|
| D01 | Guest/Customer xem B01/B02/B09 khi combo công khai; chỉ Owner có authority `catalog:bundle:manage` được dùng B03-B08, tìm SKU quản trị và upload banner | Reviewer xác nhận cách hiểu storefront so với permission matrix đã ghi tại mục 2; token không hợp lệ trả 401, thiếu quyền 403; không dùng fallback user |
| D02 | Product BUNDLE + BundleConfiguration riêng; mã trim, phân biệt hoa/thường theo unique DB hiện có, bất biến sau create; 1..100 SKU distinct, mỗi quantity 1..10,000, cấm nesting; category ACTIVE, brand optional ACTIVE | Giữ item ID theo variantId khi PUT; item mới sinh UUID, item bị bỏ mới xóa. Không yêu cầu client gửi item ID ở combo cố định |
| D03 | Giá gói dương và không cao hơn tổng lẻ khi create/update/publish; VND, BigDecimal scale tối đa 2, HALF_UP; % một số lẻ; evaluation số bộ 1..10,000 | Kiểm tra tràn số và DECIMAL sau nhân; giá SKU giảm sau publish không ẩn combo: trả purchasable=false, priceIssue và saving/discount=null; B09 vẫn trả 200 preview, không tạo giao dịch |
| D04 | Hai ngày bắt buộc cho dữ liệu mới, Instant UTC, khoảng [from,until); cho publish trước validFrom; draft được lưu ngày quá hạn, publish không được | Clock cố định trong test; now=from được xem, now=until không được xem |
| D06-read | Chỉ CHECK_COMPONENT_STOCK; đọc một kho từ `CATALOG_BUNDLE_WAREHOUSE_CODE`, mặc định WH_MAIN; không cộng chéo kho, không reserve | Thiếu dòng kho là UNKNOWN, dòng tồn 0 là OUT_OF_STOCK; available dùng max(0,onHand-reserved) cho hiển thị, không sửa số tồn DB |
| D08 | PUT chỉ DRAFT/UNPUBLISHED; kiểm tra expectedVersion và tăng header version cho cả thay đổi item/ảnh; kiểm tra stale trước no-op publish/unpublish | Hai write cùng version chỉ một thắng; không có lost update khi chỉ sửa child |
| D09-audit | AuditApi ghi đồng bộ trong transaction caller, propagation MANDATORY; lỗi audit rollback toàn bộ write | Có before/after và actor ID Long thật; no-op không ghi audit lặp; không ghi credential hoặc bản ghi User đầy đủ |
| D09-media | Server upload Cloudinary, trả asset reference; dùng ProductImage hiện có, thêm MediaAsset quản lý upload/attachment/cleanup | Không nhận URL tùy ý; chỉ asset do actor upload được gắn lần đầu, ảnh đã gắn combo được Owner khác quản lý; không xóa asset đang có reference |
| D10 | Có API lookup/upload cụ thể bên dưới; migration SQL có version và chạy có kiểm soát; auth xác thực bearer qua issuer tin cậy | Không thêm login/refresh-token hoặc nền tảng Sales; triển khai auth provider là điều kiện môi trường bắt buộc, không dùng mock làm bằng chứng vận hành |

### 3.3 Quản trị combo nội bộ — core

| Nội dung | Quy tắc đề nghị đưa vào review |
|---|---|
| Hai loại trạng thái | Product.status ACTIVE/INACTIVE/DELETED quản lý nội bộ; BundleConfiguration.publicationStatus DRAFT/PUBLISHED/UNPUBLISHED quản lý công khai. ACTIVE không đồng nghĩa PUBLISHED |
| Tạo | Product BUNDLE + ACTIVE và cấu hình DRAFT; ít nhất một SKU hợp lệ; giá gói bắt buộc. Không cần banner/ngày để lưu core; không publish tự động |
| Sửa | B06 chỉ DRAFT/UNPUBLISHED, ACTIVE hoặc INACTIVE; full replacement các field core, mã bất biến; giữ item ID nếu variantId không đổi; tăng version aggregate trong cùng transaction |
| Bật/tắt | B12 đổi Product.status ACTIVE↔INACTIVE; giữ publicationStatus; bật lại DRAFT vẫn không công khai. Không dùng B12 để publish/unpublish hoặc xóa |
| Xóa mềm | B11 đặt Product.status=DELETED, giữ publication/items/ảnh/code để truy vết; cấm hard delete, cascade xóa SKU/tồn/audit hoặc giải phóng mã. Không phục hồi trong scope hiện tại |
| Đã xóa | B03/B04/C01 không hiển thị product DELETED; sửa/bật tắt/publish trả 404. DELETE lặp chỉ được trả tombstone khi expectedVersion hiện tại khớp; version cũ luôn 409 |
| Combo đang PUBLISHED về sau | Sửa definition vẫn cần unpublish. DELETE/INACTIVE được phép ngừng bán ngay mà không đổi snapshot Sales cũ; không tự hủy đơn/reservation. ACTIVE trở lại PUBLISHED có thể hiển thị nếu còn đủ điều kiện hiệu lực; Owner phải thấy publicationStatus trong response |
| Side effect | Tạo/sửa/xóa/bật tắt không gọi Inventory, Cloudinary, Sales; chưa gọi IAM/Audit. Không tạo SKU hoặc inventory đại diện combo |
| Giá SKU thay đổi | GET hiển thị giá lẻ hiện tại; giá gói vượt tổng lẻ trả priceIssue, saving/discount null. Create/update phải hợp lệ lúc ghi; không cam kết khóa giá hoặc giá checkout |
| Dữ liệu cũ | Cho phép mở admin detail khi giá/ngày legacy null để Owner sửa. Quantity/references sai phải được preflight báo để xử lý trước constraint, không tự sửa thành 1 |

| From | Action | To | Điều kiện |
|---|---|---|---|
| Chưa có | B05 | ACTIVE + DRAFT | Định nghĩa core hợp lệ, không yêu cầu actor hiện tại |
| ACTIVE | B12 INACTIVE | INACTIVE | Version khớp; timestamp/version; không đổi publication |
| INACTIVE | B12 ACTIVE | ACTIVE | Version khớp; timestamp/version; không đổi publication |
| ACTIVE/INACTIVE | B11 | DELETED | Version khớp; timestamp/version; giữ dữ liệu vật lý |
| DELETED | B11 lặp cùng version hiện tại | DELETED | 200 success envelope không data, không tăng version lần hai |
| DELETED | B06/B12 hoặc restore | Không cho phép | 404; chưa có restore API |

### Trạng thái và xử lý khi không thành công

| Đối tượng / từ trạng thái | Hành động | Trạng thái sau | Điều kiện và xử lý lỗi |
|---|---|---|---|
| Resolution / PROPOSED | S04 đồng ý | ACCEPTED | Còn hạn, version khớp, giá đúng acceptedTotal, rule còn hợp lệ; lưu actor/thời gian/nội dung đồng ý |
| Resolution / PROPOSED hoặc ACCEPTED | Quá expiresAt | EXPIRED | Read có thể tính trạng thái hiệu lực; mọi write kiểm tra thời gian trước ghi, không phụ thuộc job |
| Resolution / ACCEPTED | S01/S05/S09 sử dụng | CONSUMED | Một tài nguyên đích duy nhất; unique reference và optimistic lock chống dùng hai lần |
| Preorder / WAITING_STOCK | S07, đủ hàng và giá không đổi | CONFIRMED | Tạo đơn và reservation cùng commit; quantity được giữ đủ toàn bộ; onHandQty chưa giảm |
| Preorder / WAITING_STOCK | S07, giá/lựa chọn không còn hợp lệ | NEEDS_RECONFIRMATION | Lưu trạng thái này rồi trả 409; không ném exception làm rollback mất trạng thái cần xác nhận; không giữ hàng |
| Preorder / NEEDS_RECONFIRMATION | S09 | WAITING_STOCK | Resolution mới đã được khách đồng ý, gắn đúng preorder; không sửa snapshot cũ |
| Preorder / WAITING_STOCK hoặc NEEDS_RECONFIRMATION | Khách hủy hoặc hết thời hạn chờ | CANCELLED hoặc EXPIRED | Chưa có reservation nên không giảm reservedQty; retry không hủy hai lần |
| Preorder / CONFIRMED | Hủy đơn chưa xuất hoặc reservation hết hạn | CANCELLED hoặc EXPIRED | Qua Sales order lifecycle: nhả đúng reservation một lần, cập nhật đơn và preorder trong cùng transaction; đã xuất hàng phải dùng quy trình trả hàng ngoài scope |

Hàng về không tự đồng nghĩa khách đã đồng ý giá mới hoặc đã có đơn. Luồng đề xuất cho phép người dùng/Staff gọi xác nhận khi đủ hàng, không bắt buộc auto-confirm bằng event. ETA chỉ là dự kiến, không cam kết ngày giao khi chưa có xác nhận của nghiệp vụ.

### Chính sách giá và hiệu lực theo trạng thái (đề xuất để duyệt)

| Trạng thái | Giá | Khi catalog unpublish/hết hiệu lực |
|---|---|---|
| Cart chưa xác nhận | Re-evaluate khi chuyển sang xác nhận; giá đổi phải hiển thị và khách chấp nhận lại. Không khóa giá chỉ vì thêm giỏ | Chặn mua mới theo proposal, giữ thông tin giỏ để giải thích; không tự xóa lịch sử |
| Quotation đã gửi | Giữ snapshot của bản đã gửi; cần quyết định cam kết giá đến validUntil hay reprice khi accept. Không mặc định cùng policy cart | Chọn tôn trọng báo giá còn hạn hoặc yêu cầu phát hành bản mới; chưa chốt thì chặn triển khai chuyển báo giá thành đơn |
| Preorder đang chờ | Phương án mục 5.1 đánh giá lại khi confirm; giá đổi chuyển NEEDS_RECONFIRMATION. Phương án khóa giá cần TTL/cam kết riêng | Phương án mục 5.1 không tạo resolution/đơn mới, cho hủy hoặc chọn combo khác; phải được PO xác nhận, không tự áp cho đơn đã xác nhận |
| Order đã xác nhận | Bảo toàn giá, SKU, số lượng đã chấp nhận; thay đổi đơn theo workflow Sales riêng | Không tự hủy/reprice hoặc sửa snapshot do thay đổi Catalog |

Ví dụ: thêm giỏ giá 5.000.000, ngày sau giá 5.500.000. Theo proposal cart, cần xác nhận lại trước mua; không thu thêm âm thầm. Nếu muốn giữ 5.000.000 phải chốt TTL và điều kiện cam kết riêng. `expectedBundleVersion` không phát hiện mọi thay đổi giá ProductVariant. Sales cần đánh giá lại toàn bộ giá server-side và so với bản khách chấp nhận, lưu thời điểm/giá SKU/giá gói/selection/thuế/rule. D07-L phải chốt xử lý giá thay đổi trong khoảng evaluate → commit (price token/revision kiểm chứng hoặc snapshot cam kết có hạn); chưa chốt thì không nghiệm thu checkout.

## 4. Data Model and Schema Impact

### Tái sử dụng schema có căn cứ

| Bảng | Vai trò UC-13 | Tham chiếu |
|---|---|---|
| `products` | Product gốc loại BUNDLE, mã, tên/mô tả, danh mục, trạng thái | DB trang 5; entity Product |
| `bundle_items` | `bundle_product_id`, `variant_id`, số lượng thành phần | DB trang 7; entity BundleItem |
| `product_variants` | SKU cấu thành và giá lẻ | DB trang 6-7 |
| `product_images` | Banner/thumbnail gắn product combo | DB trang 7 |
| `inventories` | Nguồn `available = on_hand - reserved` qua inventory API | DB trang 9; không đọc repository chéo module |

**Schema hiện tại và mục tiêu:** bundle_items lưu cấu phần cố định; workspace đã có BundleConfiguration lưu giá gói/publication/version và script migration core. Không tạo SKU đại diện combo. Nhóm option, thay thế, media và giao dịch vẫn là thiết kế mở rộng; field có sẵn không chứng minh luồng đã triển khai hoặc live DB đã migrate.

### Schema core hiện có và phương án mở rộng

BundleConfiguration, constraint BundleItem và migration core đã có trong workspace; xem runbook Phase 1. Option/media/Sales vẫn cần review và migration riêng; không sửa SQL/checksum đã phát hành để đồng bộ tài liệu.

- `bundle_configurations`: `bundle_product_id UUID PK/FK products`, `package_price DECIMAL(15,2)`, `valid_from/valid_until TIMESTAMPTZ`, `publication_status`, `stock_policy`, `version BIGINT`, thời gian tạo/sửa. Currency VND đề xuất cố định cho scope này.
- Giữ `bundle_items` cho thành phần mặc định. UNIQUE `(bundle_product_id, variant_id)`, CHECK `quantity > 0`, FK tới products/variants trong catalog; kiểm tra product loại BUNDLE và không nesting ở service.
- Nếu duyệt option: cần `bundle_component_groups` và bảng liên kết SKU được phép cho từng nhóm, cùng số lượng bắt buộc/default. Quan hệ với `bundle_items` phải chốt để không có hai nguồn sự thật.
- Nếu cần quản lý vòng đời Cloudinary asset: bổ sung metadata asset/public ID qua thiết kế media được duyệt; `image_url` đơn thuần chưa đủ cho cleanup/ownership.
- Index đề xuất trên publication/hiệu lực và `bundle_items.bundle_product_id`; chỉ chốt sau thiết kế query. Version trên cấu hình không tự phát hiện thay đổi giá SKU: đánh giá giá cần snapshot/revalidation riêng.

Flyway quản lý toàn bộ migration schema qua V1/V2 trong `db/migration`; không dùng runner hoặc SQL thủ công. Chưa xác minh trạng thái live DB. Cách khởi động/baseline nằm trong [migration runbook](../migrations/flyway.md). Phần options chờ D05 nhưng không chặn core. Không dùng `specifications` JSONB để nhét field giá/hiệu lực nhằm tránh migration.

Cross-module references chỉ dùng ID/DTO. Không tạo JPA relationship sang inventory, sales, IAM hay audit.

### 4.1 Owned Entities (Internal Package)

Bảng này cụ thể hóa phương án mở rộng ở trên. Đã kiểm tra lại danh sách Java entity trong workspace ngày 2026-10-16; chưa có module Sales hoặc entity reservation chuyên biệt. Trước khi triển khai phải kiểm tra lại repo: nếu module phụ thuộc đã bổ sung entity tương đương thì mở rộng entity đó, không tạo bản sao chỉ dành cho combo. Các bảng trong Database.pdf không được coi là Java entity đã tồn tại.

| Entity / module sở hữu | Xử lý | Dữ liệu và ràng buộc cần có |
|---|---|---|
| `Product`, `Category`, `Brand` / catalog | Dùng lại | Combo là `ProductType.BUNDLE`; giữ mã/tên/mô tả/danh mục; không tạo `ComboProduct` hay SKU giả để lưu giá |
| `ProductVariant` / catalog | Dùng lại | Nguồn SKU, giá lẻ, màu/kích thước và trạng thái; không sao chép SKU vào bảng master mới |
| `BundleItem` / catalog | Dùng lại, sửa validation | ID thành phần hiện có; quantity nguyên dương, unique combo + variant; bỏ tự sửa quantity sai thành 1 trong luồng ghi |
| `ProductImage` / catalog | Dùng lại | Ảnh gắn product combo; URL/thứ tự/primary hiện có; thêm `mediaAssetId` tham chiếu asset đã xác minh |
| `Inventory`, `StockMovement` / inventory | Dùng lại | Đọc `onHandQty - reservedQty` qua API; không tạo bảng tồn combo; xem/publish không tạo movement |
| `User` / iam | Dùng lại qua context/API | Actor thực đã xác thực; không tạo tài khoản giả để cấp quyền Owner |
| `BundleConfiguration` / catalog | Đã có core trong workspace | Bảng `bundle_configurations` như trên; một cấu hình cho một Product BUNDLE; `@Version` bao trùm thay đổi aggregate |
| `MediaAsset` / catalog | Thêm nếu chưa có provider dùng lại | `id`, Cloudinary public ID, URL do server xác minh, uploader ID, trạng thái STAGED/ATTACHED, thời gian; không chứa credential; dùng cho ảnh catalog nói chung |
| `AuditEntry` / audit | Thêm nếu chưa có provider dùng lại | ID, actor ID, action, aggregate ID, before/after, occurredAt; ghi cùng transaction nghiệp vụ qua `audit.api`, không import entity audit |
| `BundleComponentGroup` / catalog | Thêm | `groupId UUID PK`, `bundleProductId`, `bundleItemId UNIQUE`, tên hiển thị; FK nội bộ tới item cùng combo; default và quantity lấy từ BundleItem |
| `BundleComponentOption` / catalog | Thêm | `groupId`, `variantId`, sortOrder; unique(groupId,variantId); FK tới group và variant; phải chứa SKU mặc định |
| `BundleSubstitutionRule` / catalog | Thêm | `id`, `bundleProductId`, `bundleItemId`, `sourceVariantId`, `replacementVariantId`, `priceAdjustment DECIMAL(15,2)`, active; unique(item,source,replacement); chỉ thay 1 SKU cho 1 SKU với cùng quantity |
| `Cart`, `CartItem`, `Quotation`, `QuotationItem`, `SalesOrder`, `SalesOrderItem` / sales | Thêm nền tảng tối thiểu nếu vẫn chưa có | Dùng entity nghiệp vụ chung của Sales; ownership, status, version, SKU ID, quantity, giá dòng; ID khác module là UUID thuần. Schema chi tiết của nền tảng Sales là dependency phải duyệt trước triển khai |
| `SalesBundleSnapshot`, `SalesBundleSnapshotItem` / sales | Thêm | Snapshot có ID, bundle ID/version, số bộ, selections, tổng lẻ/gói, currency, thời điểm; dòng có item gốc, SKU gốc/đã chọn, qty, giá lẻ, giá phân bổ, lý do thay thế. Cart/quotation/order tham chiếu snapshot; mỗi lần khách chấp nhận giá tạo snapshot mới, không sửa snapshot cũ |
| `BundleResolution`, `BundleResolutionItem` / sales | Thêm | Phương án giải quyết thiếu hàng: actor/cart ID, bundle/version, lựa chọn, số bộ, snapshot giá đề nghị, version, trạng thái, expiresAt, acceptedAt; dòng gốc/thay thế/thiếu và nguồn rule |
| `Preorder`, `PreorderItem` / sales | Thêm | Chủ sở hữu, resolution/snapshot ID, trạng thái/version, thời hạn chờ, ETA nullable, reservation ID nullable, order ID nullable; dòng SKU và quantity cần chờ; không sao chép số tồn |
| `StockReservation`, `StockReservationItem` / inventory | Thêm | Reservation ID, business reference, request key, trạng thái ACTIVE/RELEASED/CONSUMED/EXPIRED, expiresAt/version; dòng SKU/kho/qty; cùng transaction với reservedQty; unique business reference để không giữ hai lần |
| `IdempotencyRecord` / sales | Thêm | Actor + operation + key UNIQUE, request hash, resource/result tham chiếu; lưu cùng transaction; retry cùng key/body nhận lại kết quả, khác body trả 409 |

Các entity Sales là candidate cần đối chiếu nền tảng Sales chung trước khi tạo; không dựng entity song song chỉ dành cho combo. Schema chi tiết của Cart/Quotation/Order là dependency phải review.

`BundleItem` là nguồn duy nhất của SKU mặc định và số lượng. Group không lưu một quantity/default độc lập. Request vẫn dùng `defaultVariantId`; service tìm BundleItem tương ứng để gắn group. Đổi default ở bản nháp cập nhật item và group cùng transaction; xóa item phải xóa group/options/rules liên quan. Group ID từ combo khác bị từ chối. Không tạo bảng `OptionSku` sao chép ProductVariant.

**Ràng buộc aggregate:** kiểm tra quantity > 0 và unique combo/SKU ở cả application lẫn DB. Mọi cập nhật item/group/option/rule phải kiểm tra và tăng `BundleConfiguration.version` trong cùng transaction; chỉ đặt `@Version` trên header mà không làm header dirty là chưa đủ. Test phải gồm hai request sửa child cùng expectedVersion.

**PUT thay toàn bộ definition:** đối chiếu item/group ID còn giữ, thêm và xóa trước khi ghi; reject ID thuộc combo khác; cập nhật/xóa group/options/rules phụ thuộc trong cùng transaction, không để reference mồ côi. Snapshot giao dịch phải giữ bản sao giá trị và ID nguồn để truy vết, không cascade delete từ definition và không đọc live item để tái dựng lịch sử. Chính sách giữ ID khi PUT phải chốt tại D08 và kiểm thử.

### 4.2 Database Migration

| Đợt | Chuyển đổi | Kiểm tra trước khi bật chức năng |
|---|---|---|
| Cấu hình cố định | Tạo cấu hình cho combo cũ ở DRAFT; giá/ngày chưa có phải được Owner bổ sung, không đoán giá. Thêm metadata ảnh theo kiểu nullable cho dữ liệu cũ | Liệt kê item qty sai/trùng/tham chiếu lỗi để xử lý có kiểm soát trước khi thêm constraint; không tự xóa dữ liệu import |
| Nhóm lựa chọn | Tạo bảng group/option rỗng; combo cũ giữ nguyên items và không có group | Response thêm group theo contract; `[]` tiếp tục là combo cố định; không tự biến mọi item thành option |
| Giao dịch và thay thế | Thêm rules và bảng giao dịch; stockPolicy bổ sung `ALLOW_PREORDER` theo cấu hình Owner, combo cũ giữ `CHECK_COMPONENT_STOCK` | Đối soát reservedQty cũ trước dùng reservation ledger; không suy ra chủ sở hữu reservation từ tổng reservedQty |
| Toàn bộ schema | Migration có version, chạy trên bản DB test đã có dữ liệu; công cụ migration do D10 chốt | Backup, kiểm tra forward migration và rollback ứng dụng. Không drop bảng/snapshot có giao dịch để rollback; dùng migration sửa tiếp khi cần |

### 4.3 Schema cấu hình cố định

Bảng dưới mô tả cả đích đăng bán: Phase 1 chỉ yêu cầu giá/thành phần core hợp lệ; không yêu cầu ngày/ảnh, không thêm ProductImage.mediaAssetId hoặc MediaAsset trong đợt core. Schema Audit đã có được giữ để tương thích migration, không yêu cầu CRUD phát audit. Các bổ sung media thuộc Phase 2.

| Thành phần | Schema / hợp đồng thực thi |
|---|---|
| BundleConfiguration | bundle_product_id UUID PK/FK products, package_price numeric(15,2), valid_from/valid_until timestamptz, publication_status varchar(20), stock_policy varchar(40), version bigint default 0, created_at/updated_at timestamptz. Giá/ngày nullable chỉ cho dữ liệu legacy DRAFT chưa cấu hình; giá nếu có >0, hai ngày nếu đủ phải from<until; PUBLISHED phải đủ field. Dữ liệu API mới vẫn bắt buộc đầy đủ |
| BundleItem | UNIQUE(bundle_product_id,variant_id), CHECK(quantity>0), FK products/variants; giữ UUID cũ và quantity; dữ liệu sai phải được báo trước migration, không tự đổi/xóa |
| ProductImage | Thêm media_asset_id UUID nullable FK media_assets, giữ toàn bộ URL cũ; ảnh legacy chưa xác minh không đủ điều kiện publish |
| MediaAsset | id UUID PK, public_id text UNIQUE, uploader_id bigint, secure_url text nullable, status varchar(24), format varchar(12), bytes bigint, width/height int, created_at/updated_at/detached_at timestamptz nullable theo lifecycle; user chỉ là ID, không JPA relationship chéo module |
| AuditEntry | id UUID PK, actor_id bigint, action varchar(40), aggregate_type varchar(40), aggregate_id UUID, before_value/after_value jsonb, occurred_at timestamptz; append-only qua service; không cascade từ Product |

### 4.4 Cross-Module References

| Tham chiếu | Kiểu / owner | Quy tắc |
|---|---|---|
| actorId, uploaderId | Long / IAM | Lưu ID; lấy danh tính qua CurrentActorApi; không JPA join User |
| variantId trong inventory/sales | UUID / catalog | Snapshot/DTO qua api; không import ProductVariant entity |
| reservationId, orderId | UUID / inventory, sales | ID thuần; snapshot không cascade từ Product hoặc BundleItem |
| MediaAsset, BundleConfiguration, BundleItem, ProductImage | Nội bộ catalog | Được FK vật lý cùng module; không xóa lịch sử giao dịch khi thay definition |

Index: cấu hình trên publication_status/validity; bundle_items theo bundle_product_id; media theo status/updated_at; audit theo aggregate_id/occurred_at. Ràng buộc unique/FK/check áp dụng sau preflight dữ liệu cũ. SQL versioned, checksum, kiểm retry và rollback được quy định trong plan; không tự chạy DDL trên production, không đưa credential vào migration.

## 5. API and Contract Specifications

### 5.1. External HTTP Endpoints (Client Facing)

**Phạm vi contract:** B03-B06 tại mục Core DTO cùng B11/B12 và C01 là contract Phase 1 hiện tại. DTO đầy đủ và endpoint public/publish/media/option/giao dịch là mục tiêu Phase 2/3, chưa được coi đã triển khai. Mô tả quyền, Bearer, CurrentActorApi hoặc audit trong thiết kế đầy đủ chỉ áp dụng khi tích hợp provider chung sau Phase 1; không dùng để thêm dependency vào CRUD core.

Tất cả JSON response dùng `ApiResponse<T>`. Core B03-B06/B11/B12/C01 đã có trong workspace; các endpoint còn lại và DTO đầy đủ là thiết kế chờ review trước khi FE sử dụng. Tên route có trong spec không chứng minh đã triển khai.

| Method / Path | Input chính | Output / HTTP | Quyền |
|---|---|---|---|
| `GET /api/v1/catalog/bundles` | `page`, `size`, `keyword`; giới hạn size đề xuất 100 | 200, trang bundle summaries đang hiệu lực | D01 |
| `GET /api/v1/catalog/bundles/{bundleId}` | UUID | 200, bundle detail và availability | D01; không lộ draft |
| `GET /api/v1/catalog/admin/bundles` | Paging, publicationStatus, productStatus, keyword | 200, trang quản trị gồm draft/unpublished | Manage |
| `GET /api/v1/catalog/admin/bundles/{bundleId}` | UUID | 200, định nghĩa quản trị và version | Manage |
| `POST /api/v1/catalog/admin/bundles` | `BundleCreateRequest` | 201, draft detail | Manage |
| `PUT /api/v1/catalog/admin/bundles/{bundleId}` | Full definition + expectedVersion | 200, detail | Manage |
| `POST /api/v1/catalog/admin/bundles/{bundleId}/publish` | expectedVersion | 200, published detail; MSG44 | Manage |
| `POST /api/v1/catalog/admin/bundles/{bundleId}/unpublish` | expectedVersion | 200, detail | Manage |
| `POST /api/v1/catalog/bundles/{bundleId}/evaluations` | selections, bundleQuantity, expectedVersion | 200, server-calculated components/price/availability | D01; preview không giữ tồn |

Các write model dùng các trường chung `productCode`, `name`, `categoryId`, `brandId`, `description`, `bannerAssetId`, `items`, `componentGroups`, `packagePrice`, `validFrom`, `validUntil`, `stockPolicy`; `BundleUpdateRequest` thêm `expectedVersion`. Kiểu, nullability và validation ở 5.1.2 là nguồn contract chi tiết. Option schema vẫn chờ D05, không chấp nhận arbitrary SKU thay thế từ client.

`BundleSummary`: `bundleId`, `productCode`, `name`, `thumbnailUrl`, `linkedItemsCount`, `originalTotal`, `packagePrice`, `savingAmount`, `discountPercent`, `availability`, `effective`, `version`. `linkedItemsCount` đề xuất đếm SKU distinct; cần phân biệt với tổng số lượng.

`BundleDetail`: summary + mô tả, ảnh, thời hạn, danh sách SKU/qty/tên/giá bán/trạng thái thiếu, các nhóm option được duyệt. `BundleEvaluation`: lựa chọn đã resolve, số bộ, giá theo số lượng, dòng SKU, timestamp đánh giá và revision; không đồng nghĩa reservation.

Pagination đề xuất: `data.items`, `page`, `size`, `totalElements`, `totalPages`. Chi tiết contract được mô tả bên dưới; mọi quy tắc mới vẫn là PROPOSED để review.

#### 5.1.1 Quy ước chung cho từng API

- **Headers:** `Accept: application/json`; request có body dùng `Content-Type: application/json`. Admin API khi tích hợp Auth sau Phase 1 dùng `Authorization: Bearer <access-token>`; core hiện tại không yêu cầu header này; public API chỉ được mở sau khi D01 được duyệt. Token trong tài liệu là placeholder, không phải credential thật.
- **Identity:** server lấy actor từ security context. Không chấp nhận `actorId`, `role`, `costPrice`, `productType`, `publicationStatus` hoặc giá tính toán từ write request. Chuyển publication phải qua action API.
- **IDs:** `bundleId`, `variantId`, `categoryId`, `brandId`, `groupId` là UUID. Path/query sai định dạng trả `400 / SYS_002`; lỗi Bean Validation trong body trả `400 / SYS_003` với `errors[].field`. Binding/JSON lỗi cần handler tương ứng trong implementation, chưa khẳng định handler hiện tại đã bao phủ.
- **Tiền:** JSON number, Java `BigDecimal`, VND, tối đa 2 chữ số thập phân và nằm trong `DECIMAL(15,2)`. Đề xuất `packagePrice > 0`; các ngoại lệ và rounding phải chốt D03. Tổng tiền và phần trăm đều do server tính. Giá trong summary/detail tính cho **một bộ**; evaluation có trường riêng cho tổng số bộ.
- **Thời gian:** ISO-8601 có offset; server normalize UTC. Đề xuất `validFrom`, `validUntil` bắt buộc, `validFrom < validUntil`, khoảng `[validFrom, validUntil)` (D04). `timestamp` trong envelope là thời điểm response; `evaluatedAt` là thời điểm snapshot đọc giá/tồn.
- **Version:** `version` là số nguyên không âm của aggregate combo. Write vào combo hiện có bắt buộc `expectedVersion`; thiếu trả SYS_003, không khớp trả `409 / CATALOG_BUNDLE_003`. Version không bao gồm thay đổi giá SKU hoặc tồn kho bên ngoài combo.
- **Transaction:** Phase 1 ghi definition/config/items nguyên tử, không gọi audit. Khi tích hợp Audit, thực hiện theo cơ chế được duyệt, không báo thành công khi ghi definition thất bại. GET/evaluation chỉ đọc, không tạo giỏ hàng, reservation hoặc stock movement.
- **Lỗi chung:** JSON lỗi dùng envelope chung. Admin API có thể trả 401/403; lỗi không kiểm soát trả `500 / SYS_001`, không lộ stack trace. Các mục API ghi thêm lỗi nghiệp vụ cụ thể; thành công không có `errors`.

#### 5.1.2 Request models

| Trường hợp | Contract đầy đủ |
|---|---|
| Combo cố định | componentGroups=[] và selections=[]; quantity thành phần do Owner xác định |
| Combo có nhóm lựa chọn | groups/selections theo whitelist; khách chỉ thay SKU trong nhóm được phép, không thêm/bớt thành phần |
| Chính sách tồn | CHECK_COMPONENT_STOCK; ALLOW_PREORDER chỉ sau D06-P được duyệt. B09 luôn là preview; B10 cấu hình rule, S01-S09 xử lý giao dịch |

##### BundleCreateRequest / BundleUpdateRequest

`BundleWriteRequest` ở bảng tổng quan là tên mô hình chung; implementation tách create/update để validation `expectedVersion` không mơ hồ.

| Field | Type / bắt buộc | Ý nghĩa và validation đề xuất |
|---|---|---|
| `productCode` | string / có | Trim, nonblank, <=100; unique theo rule product hiện có; không tự thay đổi case khi chưa chốt |
| `name` | string / có | Tên combo, trim/nonblank, <=255 |
| `categoryId` | UUID / có | Danh mục tồn tại, ACTIVE |
| `brandId` | UUID hoặc null / không | Nếu có phải tồn tại, ACTIVE; combo nhiều hãng có thể null |
| `description` | string hoặc null / không | Mô tả; giới hạn đề xuất 10,000 ký tự; không cho phép script/HTML tùy ý |
| `bannerAssetId` | string hoặc null / không khi tạo draft | ID tham chiếu media đã xác minh, không nhận URL tùy ý; bắt buộc khi publish |
| `items` | array / có, 1..100 | Mỗi item có `variantId: UUID`, `quantity: integer 1..10,000`; reject SKU trùng/nesting; SKU phải ACTIVE và thuộc product NORMAL đang ACTIVE |
| `packagePrice` | decimal / có | Giá ưu đãi một bộ; validation theo D03 |
| `validFrom`, `validUntil` | datetime / có | Hiệu lực theo quy ước trên; draft có thể đã hết hạn nhưng không được publish nếu `validUntil <= now` |
| `stockPolicy` | enum / có | Đề xuất `CHECK_COMPONENT_STOCK`; các chế độ preorder/bỏ kiểm tra cần contract D06 riêng, không nhận string tùy ý |
| `componentGroups` | array / có, có thể rỗng | Nhóm thay thế theo phương án D05 bên dưới; `[]` biểu thị combo cố định |
| `expectedVersion` | integer / chỉ update | >=0; create không nhận field này |

**Option model đề xuất D05:** mỗi group có `groupId` (UUID, giữ ổn định qua các lần edit), `defaultVariantId` (phải có trong `items`) và `allowedVariantIds` (UUID distinct, chứa default). Mỗi item mặc định thuộc tối đa một group. Chọn option **thay thế** SKU mặc định và giữ quantity của item, không thêm một dòng mặc định nữa. Mọi option phải là SKU hợp lệ; nếu lựa chọn cuối cùng trùng SKU giữa các thành phần, tạm đề xuất reject thay vì tự cộng. Model hiện minh họa phương án giá cố định; chưa cho phép triển khai trước khi Owner chọn A/B/C tại D05-P. Phụ phí cần mở rộng model; cấm khác giá cần bổ sung validation và kiểm thử. Đây là phương án để quyết định, chưa khẳng định SRS quy định cấu trúc này.

Ví dụ create combo cố định (IDs minh họa; khi chạy phải tồn tại):

```json
{
  "productCode": "BUNDLE-BATH-001",
  "name": "Combo phòng tắm",
  "categoryId": "10000000-0000-4000-8000-000000000001",
  "brandId": null,
  "description": "Combo thiết bị phòng tắm",
  "bannerAssetId": "40000000-0000-4000-8000-000000000001",
  "items": [
    {"variantId": "20000000-0000-4000-8000-000000000001", "quantity": 1},
    {"variantId": "20000000-0000-4000-8000-000000000002", "quantity": 1}
  ],
  "packagePrice": 95000000.00,
  "validFrom": "2026-11-01T00:00:00+07:00",
  "validUntil": "2026-12-01T00:00:00+07:00",
  "stockPolicy": "CHECK_COMPONENT_STOCK",
  "componentGroups": []
}
```

PUT gửi đầy đủ cùng các field trên và `expectedVersion`. Field nullable bị bỏ qua có nghĩa xóa giá trị về null; không dùng PUT như partial patch. `items` và `componentGroups` thay thế toàn bộ collection trong một transaction; nhóm bị xóa khiến lựa chọn cũ không còn hợp lệ. `productCode` đề xuất bất biến sau create; PUT phải gửi lại cùng mã (D02).

##### BundleVersionRequest

Dùng cho publish/unpublish: `{"expectedVersion": 3}`. Không nhận kèm definition hoặc stock policy để tránh thay đổi cấu hình qua action endpoint.

##### BundleEvaluationRequest (HTTP)

| Field | Type / bắt buộc | Validation và ý nghĩa |
|---|---|---|
| `bundleQuantity` | integer / có | 1..10,000; server kiểm tra overflow tổng quantity/giá |
| `selections` | array / có, có thể rỗng | `{groupId: UUID, variantId: UUID}`; tối đa một lựa chọn/group; group không có selection dùng default |
| `expectedVersion` | integer / không | Nếu có phải khớp; nếu bỏ qua evaluate revision hiện tại. Sales consumer đề xuất bắt buộc gửi revision đã chọn |

Bundle ID lấy từ path, không lặp trong HTTP body. Java DTO có `bundleId` vì không có path. Không nhận `packagePrice`, `discountPercent` hoặc số lượng SKU tự khai từ client.

```json
{
  "bundleQuantity": 2,
  "selections": [],
  "expectedVersion": 3
}
```

#### 5.1.3 Response models

| Model | Trường và ngữ nghĩa |
|---|---|
| `BundleSummary` | `bundleId: UUID`, `productCode/name: string`, `thumbnailUrl: string?`, `linkedItemsCount: integer` (SKU mặc định distinct), `originalTotal/packagePrice/savingAmount: decimal` (một bộ), `discountPercent: decimal?`, `availability: enum`, `effective: boolean`, `version: integer`. `discountPercent` null nếu không thể tính theo D03; tuyệt đối không chia cho 0 |
| `BundleDetail` | Summary + `description: string?`, `images: [{imageUrl,isPrimary,sortOrder}]`, `validFrom/validUntil: datetime`, `stockPolicy: enum`, `items: [BundleComponentView]`, `componentGroups` như request kèm metadata hiển thị SKU được phép, `availableBundleQty: integer?`, `evaluatedAt: datetime`. Read response phản ánh cấu hình mặc định; lựa chọn khác dùng evaluation |
| `BundleComponentView` | `variantId: UUID`, `sku/name: string`, `quantityPerBundle: integer`, `unitPrice: decimal` (giá lẻ), `availability: enum`. Public response không cần lộ số tồn chính xác từng SKU, kho hay giá vốn |
| `BundleAdminDetail` | Detail + `categoryId`, `brandId?`, `bannerAssetId?`, `publicationStatus`, `productStatus`, `createdAt`, `updatedAt`, `publishBlockers: [{code,field,message}]`. Không dùng blockers thay validation khi publish; luôn kiểm tra lại tại write |
| `BundleAdminSummary` | Summary + `publicationStatus`, `productStatus`, `validFrom`, `validUntil`; cho phép quản trị biết vì sao không hiển thị công khai |
| `BundlePage<T>` | `items: T[]`, `page: integer`, `size: integer`, `totalElements: integer`, `totalPages: integer`; rỗng trả items=[], totalElements=0, totalPages=0 |
| `BundleEvaluation` | `bundleId`, `version`, `bundleQuantity`, `currency: "VND"`, `unitOriginalTotal`, `unitPackagePrice`, `totalOriginalPrice`, `totalPackagePrice`, `totalSavingAmount`, `discountPercent`, `components: [{variantId,sku,name,quantityPerBundle,totalQuantity,unitPrice,availability}]`, `resolvedSelections`, `availability`, `availableBundleQty: integer?`, `evaluatedAt`. Tổng số lượng và giá nhân bundleQuantity; unitPrice là giá lẻ, chưa phải giá đã phân bổ discount của sales |

**Availability đề xuất:** component dùng `IN_STOCK`, `OUT_OF_STOCK`, `UNKNOWN`; combo dùng `IN_STOCK`, `PARTIALLY_OUT_OF_STOCK`, `OUT_OF_STOCK`, `UNKNOWN`. Nếu có dữ liệu tồn thiếu/lỗi, combo UNKNOWN và availableBundleQty=null; nếu đầy đủ dữ liệu: không SKU nào đủ quantity yêu cầu thì OUT_OF_STOCK, một số thiếu thì PARTIALLY_OUT_OF_STOCK, mọi SKU đủ thì IN_STOCK. Với evaluation, quantity yêu cầu là quantityPerBundle × bundleQuantity. GET mặc định đánh giá một bộ. Đây là chỉ báo khả dụng, không phải reservation.

Ví dụ envelope thành công khi publish (trích các trường `data` để minh họa; schema đầy đủ là BundleAdminDetail ở trên):

```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Bathroom package created successfully.",
  "data": {
    "bundleId": "30000000-0000-4000-8000-000000000001",
    "publicationStatus": "PUBLISHED",
    "effective": true,
    "version": 4
  },
  "timestamp": "2026-11-02T03:00:00Z"
}
```

Ví dụ lỗi validation (field path dùng index bắt đầu từ 0):

```json
{
  "success": false,
  "code": "SYS_003",
  "message": "Dữ liệu đầu vào không hợp lệ",
  "errors": [{"field": "items[0].quantity", "message": "Số lượng phải lớn hơn hoặc bằng 1"}],
  "timestamp": "2026-11-02T03:00:00Z"
}
```

#### API-B01 - Danh sách combo công khai

| Nội dung | Chi tiết |
|---|---|
| Operation ID | `listPublishedBundles`; `GET /api/v1/catalog/bundles`; phục vụ R01/R05. |
| Quyền/header | public read theo D01; Accept JSON; không có request body. |
| Query | `page` mặc định 0, >=0; `size` mặc định 20, 1..100; `keyword` optional, trim, <=255, tìm chuỗi trong productCode/name. Đề xuất chưa nhận sort tùy ý: sắp `createdAt DESC, bundleId ASC` cố định. Query ngoài contract phải được thống nhất reject/ignore trước approval; không dùng để bỏ lọc visibility. |
| Xử lý | lọc BUNDLE + product ACTIVE + publication PUBLISHED + hiệu lực tại một thời điểm query; phân trang product trước, sau đó batch-load item/giá/tồn; không làm sai totalElements do join nhiều item. |
| Response | 200 `ApiResponse<BundlePage<BundleSummary>>`; không có kết quả hoặc page vượt cuối trả trang rỗng, không phải 404. Hiển thị combo thiếu tồn với availability, không ẩn chỉ vì thiếu tồn. |
| Lỗi | 400 SYS_002 cho paging/query sai; lỗi inventory làm availability UNKNOWN; lỗi DB/catalog vẫn 500, không giả danh sách rỗng. Không mutation/audit giá. |
| Test | AC03/AC04/AC05/AC08, thêm paging rỗng, ổn định và không lộ combo draft. |

#### API-B02 - Chi tiết combo công khai

| Nội dung | Chi tiết |
|---|---|
| Operation ID | `getPublishedBundle`; `GET /api/v1/catalog/bundles/{bundleId}`; phục vụ R01/R05/R06/R08. |
| Quyền/header | public read theo D01; Accept JSON; path UUID, không body/query nghiệp vụ. |
| Xử lý | áp cùng visibility như API-B01; resolve SKU mặc định và option cho phép, tính giá lẻ/tiết kiệm, batch lookup tồn, trả ảnh và thời hạn. Không trả metadata quản trị hoặc SKU thay thế ngoài nhóm được phép. |
| Response | 200 `ApiResponse<BundleDetail>`; inventory lỗi vẫn trả definition với UNKNOWN. Các option thay thế cụ thể chỉ được công bố sau D05, không tự sinh khuyến nghị AI. |
| Lỗi | 400 SYS_002 nếu UUID lỗi; 404 SYS_004 nếu không tồn tại, không phải BUNDLE, draft/unpublished/inactive/deleted hoặc ngoài hiệu lực. Không tiết lộ trạng thái quản trị trong response 404. |
| Test | AC03-06/AC08, bao gồm gọi trực tiếp ID draft và combo hết hạn. |

#### API-B03 - Danh sách combo quản trị

| Nội dung | Chi tiết |
|---|---|
| Operation ID | `listAdminBundles`; `GET /api/v1/catalog/admin/bundles`; phục vụ R01 và quản lý lifecycle. |
| Quyền/header | Bearer + `catalog:bundle:manage`; không body. |
| Query | page/size/keyword và sort cố định như B01; thêm `publicationStatus` optional (`DRAFT`, `PUBLISHED`, `UNPUBLISHED`) và `productStatus` optional (`ACTIVE`, `INACTIVE`). Đề xuất không liệt kê product DELETED; không nhận query `status` mơ hồ. |
| Xử lý | đọc mọi combo chưa DELETED trong phạm vi quản trị, không bắt buộc đang hiệu lực; filter rồi paging và batch tính giá/tồn. |
| Response | 200 `ApiResponse<BundlePage<BundleAdminSummary>>`; kết quả rỗng trả 200. |
| Lỗi | 401/403; 400 SYS_002 cho enum/paging sai; inventory lỗi chỉ làm UNKNOWN. Read-only, không tự chuyển trạng thái combo hết hạn. |
| Test | AC03/AC04/AC08 và bộ lọc phân biệt publication với product status. |

#### API-B04 - Chi tiết combo để chỉnh sửa

| Nội dung | Chi tiết |
|---|---|
| Operation ID | `getAdminBundle`; `GET /api/v1/catalog/admin/bundles/{bundleId}`; phục vụ R02-R05. |
| Quyền/header | Bearer + Manage; path UUID; không body. |
| Xử lý | tải definition đầy đủ kể cả DRAFT/UNPUBLISHED/ngoài hiệu lực, version và media reference; tính blockers để Owner chuẩn bị publish. Đề xuất không trả product DELETED. |
| Response | 200 `ApiResponse<BundleAdminDetail>`; version dùng cho PUT/action tiếp theo, không coi blockers ở lần đọc là bảo đảm lần publish sau sẽ thành công. |
| Lỗi | 401/403, 400 SYS_002, 404 SYS_004 nếu không tồn tại/không phải BUNDLE/DELETED; inventory lỗi vẫn trả definition với UNKNOWN. |
| Test | AC03/AC08/AC09; GET không làm tăng version hoặc thay đổi lifecycle. |

#### API-B05 - Tạo định nghĩa combo

| Nội dung | Chi tiết |
|---|---|
| Operation ID | `createBundle`; `POST /api/v1/catalog/admin/bundles`; phục vụ R02-R04. |
| Quyền/header | Bearer + Manage, JSON; body `BundleCreateRequest` tại 5.1.2. |
| Xử lý | validate field → kiểm tra category/brand/SKU/options/media → tính giá lẻ → ghi Product BUNDLE, cấu hình DRAFT, items/groups và liên kết ảnh trong một transaction. Server đặt product ACTIVE theo phương án đề xuất, publication vẫn DRAFT nên không công khai. Không gọi reserveStock, không tạo synthetic tồn combo. |
| Response | 201 `ApiResponse<BundleAdminDetail>`, `Location: /api/v1/catalog/admin/bundles/{bundleId}`. Message đề xuất “Đã tạo bản nháp combo.”; MSG44 dành cho publish theo SRS. Version khởi tạo do persistence quản lý, client dùng giá trị server trả về. |
| Lỗi | 401/403; SYS_003 lỗi body; CATALOG_BUNDLE_001 SKU/options sai; CATALOG_BUNDLE_002 giá/ngày/category/brand không hợp lệ; CATALOG_BUNDLE_007 asset không hợp lệ; DB_001 trùng mã; CATALOG_BUNDLE_005 nếu không xác minh được media bắt buộc. Lỗi write rollback toàn bộ definition. |
| Retry | chưa hỗ trợ Idempotency-Key trong contract này; retry cùng productCode sau lần tạo thành công nhận 409, không tạo bản sao. Client tra cứu quản trị để xác minh khi mất response; không coi 409 là replay response thành công. |
| Test | AC01/AC02/AC10/AC12, duplicate code, rollback ở item/group/image và không phát sinh stock movement. |

#### API-B06 - Thay thế định nghĩa combo

| Nội dung | Chi tiết |
|---|---|
| Operation ID | `updateBundle`; `PUT /api/v1/catalog/admin/bundles/{bundleId}`; phục vụ chỉnh sửa R02-R04. |
| Quyền/header | Bearer + Manage, JSON; path UUID và body `BundleUpdateRequest` đầy đủ. |
| Precondition đề xuất D08 | chỉ sửa DRAFT/UNPUBLISHED; combo PUBLISHED cần unpublish trước. Không tự thêm workflow revision song song. Product DELETED trả 404. |
| Xử lý | kiểm tra version, lifecycle và mã bất biến → validate toàn bộ definition như create → thay thế items/groups/media metadata nguyên tử → tăng version một lần khi ghi thành công. Giữ publication hiện tại; không tự publish sau edit. |
| Response | 200 `ApiResponse<BundleAdminDetail>` với version mới. Sales snapshots cũ không đổi; media cũ chỉ cleanup nếu không còn được tham chiếu theo D09. |
| Lỗi | các lỗi validate/reference/media như B05; thêm 404 SYS_004, 409 CATALOG_BUNDLE_003 nếu stale, 409 CATALOG_BUNDLE_006 nếu đang PUBLISHED. Nếu đồng thời vừa publish vừa edit, chỉ một write được thắng trên cùng version. |
| Retry | request dùng lại version cũ trả 409 kể cả body giống lần trước; client đọc lại definition. Không hứa idempotency replay tự động. |
| Test | AC01/AC09-12; nullable clear, collection replacement, không mất items nếu write thất bại. |

#### API-B07 - Publish combo

| Nội dung | Chi tiết |
|---|---|
| Operation ID | `publishBundle`; `POST /api/v1/catalog/admin/bundles/{bundleId}/publish`; phục vụ R05. |
| Quyền/header | Bearer + Manage, JSON; `BundleVersionRequest`. |
| Xử lý | kiểm tra version → tồn tại/BUNDLE/product ACTIVE → validate lại giá, SKU/default/options, banner đã xác minh và validUntil trong tương lai → chuyển DRAFT/UNPUBLISHED sang PUBLISHED trong transaction. Đề xuất cho publish trước validFrom nhưng `effective=false` tới khi bắt đầu (D04). |
| Tồn kho | không reserve và không bắt buộc đủ tồn để publish; SRS cho phép combo xuất hiện cùng thông báo thiếu tồn. Inventory outage không biến publish thành lời bảo đảm còn hàng. |
| Response | 200 `ApiResponse<BundleAdminDetail>`, message MSG44, version mới. PUBLISHED chỉ thể hiện trạng thái catalog; khả năng dùng trong POS/checkout cần kiểm chứng AC07. |
| Retry | nếu đang PUBLISHED và expectedVersion khớp version hiện tại, trả 200 cùng version, không ghi audit chuyển trạng thái lần hai. Nếu request retry dùng version trước khi publish, trả 409 stale; thứ tự kiểm tra version trước no-op. |
| Lỗi | 401/403, 404 SYS_004, 400 SYS_003; 409 CATALOG_BUNDLE_003; 409 CATALOG_BUNDLE_006 nếu product INACTIVE; 400 CATALOG_BUNDLE_001/002/007 nếu definition không đủ điều kiện; 503 CATALOG_BUNDLE_005 nếu media verification bắt buộc không khả dụng. |
| Test | AC03/AC04/AC09/AC11/AC12; future publish, expired definition, publish lặp và không giữ tồn. |

#### API-B08 - Ngừng công khai combo

| Nội dung | Chi tiết |
|---|---|
| Operation ID | `unpublishBundle`; `POST /api/v1/catalog/admin/bundles/{bundleId}/unpublish`; hỗ trợ quản lý combo đã publish. |
| Quyền/header | Bearer + Manage, JSON; `BundleVersionRequest`. |
| Xử lý | kiểm tra version và combo tồn tại → PUBLISHED thành UNPUBLISHED; tăng version và audit. Cho phép unpublish cả combo product INACTIVE/đã hết hạn để quản trị; không hard-delete product hoặc tự hủy giỏ/đơn/báo giá cũ. |
| Response | 200 `ApiResponse<BundleAdminDetail>`, effective=false. Sau commit public API không trả combo; nếu bổ sung cache thì cache invalidation thuộc cùng scope implementation. |
| Retry | UNPUBLISHED + version khớp trả 200 no-op, không tăng version/audit lần hai; DRAFT trả 409 CATALOG_BUNDLE_006. Version cũ luôn 409 CATALOG_BUNDLE_003. |
| Lỗi | 401/403, 400 SYS_003/SYS_002, 404 SYS_004, 409 CATALOG_BUNDLE_003/006. Không yêu cầu Cloudinary/inventory đang hoạt động mới cho phép unpublish. |
| Test | AC03/AC09/AC11; public visibility sau commit và snapshot sales vẫn nguyên vẹn. |

#### API-B09 - Đánh giá lựa chọn và giá combo

| Nội dung | Chi tiết |
|---|---|
| Operation ID | `evaluateBundle`; `POST /api/v1/catalog/bundles/{bundleId}/evaluations`; phục vụ R03/R06/R08 và dữ liệu tích hợp R07/R09. |
| Quyền/header | quyền đọc theo D01, JSON; body evaluation tại 5.1.2. Endpoint public này chỉ là **preview**. |
| Xử lý | kiểm tra visibility và optional expectedVersion → resolve lựa chọn/default → kiểm tra nhóm/allowed SKU/quantity → tính giá một bộ và tổng số bộ → đọc tồn batch → trả kết quả. Không nhận lựa chọn group không tồn tại, group lặp hay SKU ngoài whitelist. |
| Response | 200 `ApiResponse<BundleEvaluation>`; insufficient stock vẫn 200 với trạng thái thiếu, không tự tạo preorder/substitute/reservation. Inventory outage/missing dữ liệu trong preview trả 200 UNKNOWN; không được dùng kết quả UNKNOWN để xác nhận giao dịch. |
| Lỗi | 400 SYS_002/SYS_003; 404 SYS_004 nếu không visible; 400 CATALOG_BUNDLE_001 nếu selection sai; 409 CATALOG_BUNDLE_003 nếu expectedVersion stale; 400 CATALOG_BUNDLE_002 nếu không tính được giá hợp lệ. Dependency cần cho definition/pricing không khả dụng trả 503 CATALOG_BUNDLE_005; lỗi DB không kiểm soát 500 SYS_001. |
| Giới hạn | kết quả không khóa giá/tồn, không có thời hạn bảo đảm giao dịch. Sales phải gọi Java API đánh giá lại, áp policy xác nhận giá và thực hiện reservation nguyên tử riêng; POST này không thay cho “Add Complete Package to Cart”. |
| Test | AC02/AC05-07/AC09-10; 2 bộ nhân đúng quantities/giá, option thay dòng chứ không cộng thêm, thay đổi tồn sau preview không được xem là đã giữ hàng. |

#### B03-B06 — contract core của đợt quản trị đầu tiên

Đây là subset được đăng ký rõ trên cùng URI, không phải một endpoint ẩn hoặc hai request tùy ý theo môi trường. Core DTO là contract của lần bàn giao đầu; DTO đầy đủ phía trên là mục tiêu mở rộng sau. Khi bổ sung ảnh/ngày/options phải version/review contract và plan, giữ tương thích payload core hoặc cấp URI version mới. Không ship hai controller cùng route. Phase core không tạo endpoint public/evaluation/publish/media.

| API | Contract core cụ thể |
|---|---|
| B03 listAdminBundles | GET admin/bundles; page 0 mặc định, size 20 (1..100), keyword trim <=255, productStatus ACTIVE/INACTIVE optional, publicationStatus DRAFT/UNPUBLISHED/PUBLISHED optional; query lạ trả 400 SYS_002. Sort createdAt DESC, bundleId ASC; filter BUNDLE và không DELETED trước paging; trả BundlePage<BundleResponse> 200, rỗng/page vượt cuối 200; không gọi inventory |
| B04 getAdminBundle | GET admin/bundles/{bundleId}; UUID; 200 BundleResponse, 404 không thấy/khác BUNDLE/DELETED; GET không tăng version/audit; không nhận availability giả |
| B05 createBundle | POST admin/bundles, JSON CoreCreateRequest; 201 ApiResponse<BundleResponse> + Location; message “Đã tạo combo.”; MSG44 vẫn dành cho publish về sau. Product ACTIVE, publication DRAFT, definition/config/items nguyên tử, không gọi Audit; retry code trùng 409 DB_001, không tự tạo bản sao |
| B06 updateBundle | PUT admin/bundles/{bundleId}, JSON CoreUpdateRequest; 200 detail mới. Version cũ 409 CATALOG_BUNDLE_003; PUBLISHED 409 CATALOG_BUNDLE_006; không thấy/đã xóa 404; full replacement core, không reset ảnh/ngày/option không thuộc contract. Nếu đã có groups sau nâng cấp thì core-only writer không được bỏ qua cấu hình đó: contract phải được nâng cấp trước |

Mọi API core B03-B06/C01/B11/B12 ở Phase 1 chấp nhận request không Authorization, không gọi CurrentActorApi/AuditApi và không yêu cầu cấu hình IAM. 401/403 do quyền combo không thuộc nghiệm thu hiện tại. Phase sau dùng provider IAM chung khi contract được chốt; không tự xây login/JWT verifier hoặc tạo User giả.

| Field CoreCreateRequest | Kiểu / validation | CoreUpdateRequest |
|---|---|---|
| productCode | String trim @NotBlank @Size(max=100), unique phân biệt hoa/thường theo DB hiện có | Bắt buộc gửi cùng mã cũ; không đổi mã |
| name | String trim @NotBlank @Size(max=255) | Bắt buộc |
| categoryId | UUID @NotNull; category ACTIVE | Bắt buộc |
| brandId | UUID nullable; brand nếu có ACTIVE | Omit/null xóa brand |
| description | String nullable @Size(max=10000), plain text | Omit/null xóa mô tả |
| items | @NotNull @Size(min=1,max=100) @Valid; item variantId UUID @NotNull, quantity @NotNull @Min(1) @Max(10000); SKU distinct, NORMAL+ACTIVE | Thay toàn bộ items nguyên tử; variant giữ nguyên thì giữ item ID |
| packagePrice | BigDecimal @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=13,fraction=2); <= tổng lẻ | Bắt buộc; tổng bằng 0 hoặc tràn số từ chối |
| expectedVersion | Create không nhận | @NotNull @PositiveOrZero Long, bắt buộc |

Request core không nhận dates/bannerAssetId/componentGroups/selections/productType/productStatus/publicationStatus/costPrice/actorId; field không có trong contract trả 400 SYS_002, không silently ignore. Trong core, ngày hiệu lực ở DB null, stockPolicy CHECK_COMPONENT_STOCK là metadata chưa được thực thi; chỉ kiểm tồn khi API tương ứng được triển khai sau.

| Response core | Field / nullability |
|---|---|
| BundleResponse | bundleId UUID, productCode/name String, productStatus, publicationStatus, version Long, linkedItemsCount int, originalTotal/packagePrice/savingAmount/discountPercent nullable theo rule giá, priceIssue nullable, createdAt/updatedAt, categoryId, brandId nullable, description nullable, items [{bundleItemId,variantId,sku,name,quantity,sellingPrice,sellable}], validFrom/validUntil nullable, componentGroups. Dùng chung cho list/get/create/update/status/publish/unpublish; giữ SKU inactive với sellable=false |
| Xóa / đổi trạng thái | DELETE trả 200 ApiResponse<Void>, message thành công và không có data. Status trả 200 ApiResponse<BundleResponse> đầy đủ |
| Không có trong core response | availability, availableBundleQty, evaluatedAt, media verification, hiệu lực public hoặc purchasable mang ý nghĩa giao dịch; không giả giá trị còn hàng |

```json
{"productCode":"BUNDLE-CORE-001","name":"Combo cơ bản","categoryId":"10000000-0000-4000-8000-000000000001","brandId":null,"description":null,"items":[{"variantId":"20000000-0000-4000-8000-000000000001","quantity":1}],"packagePrice":900000.00}
```

#### API-B11 — Xóa mềm combo

| Thuộc tính | Contract |
|---|---|
| Endpoint / operation | DELETE /api/v1/catalog/admin/bundles/{bundleId}; deleteBundle |
| Input | UUID path; query expectedVersion Long >=0 bắt buộc; không DELETE body để tránh client/proxy bỏ body; Phase 1 không yêu cầu Authorization |
| Transaction | Lock/compare aggregate version, giữ Product/BundleConfiguration/items/code; đổi Product.status DELETED và tăng header version nguyên tử; Phase 1 không ghi audit |
| Response | 200 ApiResponse<Void>, message “Đã xóa combo.”; không vật lý xóa dữ liệu, không giải phóng mã |
| Lỗi / retry | 400 SYS_002 path/query sai hoặc thiếu expectedVersion; 404 không tồn tại/không BUNDLE; 409 CATALOG_BUNDLE_003 stale. Lookup nội bộ cho phép thấy tombstone để kiểm version trước no-op; retry version trước delete vẫn 409, version hiện tại 200 no-op, không tăng version |

#### API-B12 — Bật/tắt combo nội bộ

| Thuộc tính | Contract |
|---|---|
| Endpoint / operation | PATCH /api/v1/catalog/admin/bundles/{bundleId}/status; changeBundleStatus |
| Input | UUID path; JSON {expectedVersion: Long>=0, productStatus: ACTIVE hoặc INACTIVE}, @NotNull/@Valid; Phase 1 không yêu cầu Authorization; DELETED không hợp lệ trong action này |
| Transaction | Kiểm version rồi đổi Product.status, tăng version header trong cùng transaction, không gọi audit ở Phase 1; không đổi publication; không reserve/cancel giao dịch |
| Response / retry | 200 ApiResponse<BundleResponse>; cùng status và current expectedVersion trả no-op; version cũ 409 trước khi xét no-op |
| Lỗi | 400 SYS_002 JSON/enum sai, 400 SYS_003 thiếu field; 404 không thấy/đã DELETED; 409 CATALOG_BUNDLE_003 stale |

#### Error contract đề xuất

| HTTP | Code | Điều kiện |
|---|---|---|
| 400 | `SYS_003` | Bean validation; `errors` gồm field/message |
| 400 | `SYS_002` | Path/query/JSON binding sai; cần handler bao phủ khi implementation |
| 401 | `AUTH_001` | Khi tích hợp Auth sau Phase 1: request chưa xác thực |
| 403 | `AUTH_002` | Khi tích hợp Auth sau Phase 1: không đủ quyền |
| 404 | `SYS_004` | Combo không tồn tại hoặc không được công khai trong public API |
| 409 | `DB_001` | Trùng product code/constraint database |
| 400 | `CATALOG_BUNDLE_001` | SKU không hợp lệ, trùng, nesting hoặc quantity không hợp lệ theo rule được duyệt |
| 400 | `CATALOG_BUNDLE_002` | Giá/thời hạn/chính sách hoặc category/brand không hợp lệ |
| 409 | `CATALOG_BUNDLE_003` | Revision stale khi cập nhật/publish |
| 409 | `CATALOG_BUNDLE_004` | Lựa chọn hoặc giá không còn hợp lệ tại thời điểm xác nhận giao dịch |
| 503 | `CATALOG_BUNDLE_005` | Dependency bắt buộc của thao tác không khả dụng (media verification, pricing hoặc inventory khi đánh giá giao dịch) |
| 409 | `CATALOG_BUNDLE_006` | Thao tác không hợp lệ với lifecycle/product status hiện tại |
| 400 | `CATALOG_BUNDLE_007` | Media reference không tồn tại hoặc không thuộc phạm vi được phép |

Không dùng thiếu tồn như lỗi toàn trang browse: trả availability và SKU thiếu. Mã mới phải triển khai `BaseErrorCode`; IAM/security cần trả envelope cả ở filter layer. MSG44 được SRS định nghĩa là “Bathroom package created successfully.”; ngôn ngữ hiển thị cuối cùng cần thống nhất, `code` thành công vẫn theo envelope chung.

#### C01-C03 - API hỗ trợ cấu hình combo

C01 là lookup core Phase 1, không yêu cầu Authorization/actor/permission. C02/C03 thuộc media Phase 2 và chỉ triển khai sau khi review provider/ownership; khi tích hợp Auth dùng quyền `catalog:bundle:manage` từ IAM chung. Tất cả dùng ApiResponse; không mở rộng toàn bộ UC12.

| ID / endpoint | Request | Response và lỗi |
|---|---|---|
| C01 `GET /api/v1/catalog/admin/bundle-skus` | keyword optional trim <=255, page>=0 mặc định 0, size 1..100 mặc định 20; tìm sku/variantName/productName, sort sku ASC, variantId ASC; query lạ trả SYS_002 | 200 BundlePage chứa variantId, productId, sku, productName, variantName, unit, color/size nullable, sellingPrice; chỉ variant ACTIVE/product NORMAL ACTIVE; không costPrice; rỗng 200; query sai 400 |
| C02 `POST /api/v1/catalog/admin/bundle-media` | multipart `file`, PNG/JPEG/WebP tối đa 5 MiB; giải mã ảnh thật, tối đa 25 megapixel; không SVG, URL hay upload trực tiếp từ client tới Cloudinary | 201 assetId string UUID, imageUrl, status STAGED; Location trỏ C03. 400 SYS_003 file sai, 413 CATALOG_MEDIA_001 quá giới hạn, 503 CATALOG_BUNDLE_005 provider lỗi |
| C03 `GET /api/v1/catalog/admin/bundle-media/{assetId}` | UUID; chỉ uploader hoặc Owner quản lý combo đang gắn ảnh | 200 assetId/imageUrl/status hoặc 404 không tồn tại/không được truy cập; dùng để kiểm tra kết quả upload |

`bannerAssetId` giữ kiểu string nhưng phải là UUID asset do C02 trả; không dùng Cloudinary public ID làm quyền truy cập. B05/B06 gắn asset trong transaction cùng ProductImage; banner bắt buộc khi publish. URL sinh từ response provider đã xác minh. Publish kiểm tra trạng thái asset trong DB; không cần gọi Cloudinary cho mỗi publish. Có thể đặt timeout upload 10 giây và không tự retry POST; người dùng upload lại nếu thất bại.

#### API-B10 - Cấu hình quy tắc thay thế

| ID / API | Input | Xử lý và output | Lỗi / quyền |
|---|---|---|---|
| B10 `PUT /api/v1/catalog/admin/bundles/{bundleId}/substitution-rules` | expectedVersion và rules đầy đủ gồm bundleItemId, sourceVariantId, replacementVariantId, priceAdjustment | Thay toàn bộ rule trong DRAFT/UNPUBLISHED; kiểm tra SKU NORMAL/ACTIVE, source là default hoặc option của item, cùng đơn vị tính, cấm tự thay/cặp trùng; tăng version và ghi audit; 200 danh sách rule + version | Manage; lỗi như B06, quantity giữ nguyên; published phải unpublish trước |

Chọn biến thể trong nhóm Owner cho phép và xử lý SKU đã chọn nhưng thiếu hàng là hai nghiệp vụ riêng. Khách không tự thêm/bớt thành phần hoặc tạo combo mới. Rule thay thế phải khớp SKU sau lựa chọn; không tự coi mọi option là SKU thay thế tương đương.

#### Quyền và dữ liệu chung

| Nội dung | Hợp đồng đề xuất |
|---|---|
| Danh tính | Customer thao tác tài nguyên của mình; Guest dùng phiên giỏ do server cấp và xác minh; Staff cần quyền Sales và phạm vi khách/giỏ cụ thể. Không nhận actor/role từ body; không biết hoặc không sở hữu tài nguyên trả 404 |
| Body đánh giá | `bundleId: UUID`, `expectedBundleVersion: long >=0`, `bundleQuantity: int >=1`, `selections: [{groupId,variantId}]`; Sales gọi catalog đánh giá lại |
| Body ghi | Các action trên tài nguyên có `expectedVersion: long >=0`; mọi POST ghi Sales có `Idempotency-Key` bắt buộc, dài 1..100 ký tự. Key được ràng buộc actor + operation; cùng key khác body trả 409 |
| Phản hồi | `ApiResponse<T>`; create 201 + Location, action/read 200. Binding/body lỗi SYS_002/SYS_003; chưa xác thực 401, thiếu quyền 403, không thấy tài nguyên 404 |
| Lỗi riêng Sales | 409 `SALES_BUNDLE_001` version cũ; 409 `SALES_BUNDLE_002` giá/rule/hiệu lực thay đổi, cần đề nghị mới; 409 `SALES_BUNDLE_003` thiếu tồn; 409 `SALES_BUNDLE_004` trạng thái không cho phép; 409 `SALES_BUNDLE_005` key khác nội dung; 503 `SALES_BUNDLE_006` dependency không khả dụng |
| Thời hạn | Resolution đề xuất hết hạn sau 15 phút hoặc khi combo hết hiệu lực, lấy mốc sớm hơn. Thời hạn chờ preorder và reservation phải được cấu hình/phê duyệt ở D06; response luôn trả expiresAt thực tế |

#### API-S01..S09 - Giao dịch Sales

| ID / API | Input và mục đích | Kết quả / tác dụng |
|---|---|---|
| S01 `POST /api/v1/sales/carts/{cartId}/bundle-items` | Body đánh giá chung; optional `acceptedResolutionId` khi đã chấp nhận thay thế | 201 nhóm giỏ gồm groupId, snapshotId, version, toàn bộ SKU, số bộ và tổng giá. Ghi nguyên tử tất cả dòng, không giữ tồn. Thiếu hàng trả 409 SALES_BUNDLE_003 để khách mở resolution; không thêm một phần |
| S02 `POST /api/v1/sales/carts/{cartId}/bundle-resolutions` | Body đánh giá chung; tạo mới cần thành phần thiếu, riêng preorderId ở NEEDS_RECONFIRMATION được tạo dù đã đủ hàng để xác nhận lại giá | 201 resolution trạng thái PROPOSED: ID/version/expiresAt, SKU thiếu, candidates hợp lệ cùng chênh giá/tồn, preorderAllowed. Không có candidate thì danh sách rỗng; không hứa ETA nếu chưa có dữ liệu |
| S03 `GET /api/v1/sales/bundle-resolutions/{resolutionId}` | UUID; kiểm tra sở hữu | 200 phương án đã lưu, trạng thái và expiresAt; GET không giữ tồn, không gia hạn; dữ liệu tồn là snapshot thời điểm đề nghị |
| S04 `POST /api/v1/sales/bundle-resolutions/{resolutionId}/acceptances` | expectedVersion, `mode: SUBSTITUTE / PREORDER`, replacements gồm itemId/variantId, `acceptedTotal: decimal` | Server tính lại; SUBSTITUTE cần phủ mọi dòng thiếu bằng candidate hợp lệ và đủ tồn tại lần đọc; PREORDER giữ nguyên lựa chọn gốc, replacements=[] và Owner cho phép. Giá/phiên bản thay đổi trả 409, không tự chấp nhận thay khách. 200 resolution ACCEPTED + snapshot giá đã đồng ý |
| S05 `POST /api/v1/sales/preorders` | acceptedResolutionId, expectedVersion của resolution | Chỉ nhận resolution PREORDER đã chấp nhận/còn hạn/chưa dùng; 201 preorder WAITING_STOCK, snapshot/expiresAt/ETA nullable. Không giữ một phần SKU đang có và không trừ tồn âm |
| S06 `GET /api/v1/sales/preorders/{preorderId}` | UUID; kiểm tra sở hữu hoặc quyền Staff | 200 trạng thái, snapshot, số lượng chờ, ETA nullable, expiresAt, version, reservationId/orderId nếu có; không lộ dữ liệu khách khác |
| S07 `POST /api/v1/sales/preorders/{preorderId}/confirmations` | expectedVersion; Customer hoặc Staff được phép | Đánh giá lại và giữ đủ tất cả SKU, tạo SalesOrder và liên kết snapshot/reservation trong một transaction; 200 preorder CONFIRMED và orderId. Thiếu hàng: 409, vẫn WAITING_STOCK; giá khác: NEEDS_RECONFIRMATION, không tạo đơn |
| S08 `POST /api/v1/sales/preorders/{preorderId}/cancellations` | expectedVersion, reason không chứa thông tin nhạy cảm | Hủy WAITING_STOCK/NEEDS_RECONFIRMATION; 200 CANCELLED. CONFIRMED phải dùng quy trình hủy đơn Sales để nhả reservation và đổi cả hai trạng thái nguyên tử; không bỏ liên kết đơn |
| S09 `POST /api/v1/sales/preorders/{preorderId}/reconfirmations` | expectedVersion, acceptedResolutionId mới thuộc cùng khách và preorder | Chấp nhận giá/lựa chọn mới qua S02/S04 rồi thay tham chiếu snapshot; snapshot cũ giữ nguyên; 200 WAITING_STOCK. Không giữ hàng ở bước này |

S02 cho phép `preorderId` optional khi cần xác nhận lại preorder; server xác minh ownership và chỉ cho tạo từ NEEDS_RECONFIRMATION. S04 không cho đổi chế độ hoặc chấp nhận lần hai; muốn thay phương án phải tạo resolution mới. Resolution đã được S01/S05/S09 dùng thì chuyển CONSUMED cùng transaction, không dùng cho giỏ/đơn khác. Không tự kết hợp thay một phần SKU và preorder phần còn lại trong lần bàn giao này: khách chọn SUBSTITUTE đủ bộ hoặc PREORDER toàn bộ cấu hình đã chọn; chỉ triển khai sau khi D06-P được duyệt; đây chưa phải policy đã chốt.

S01 có acceptedResolutionId chỉ nhận mode SUBSTITUTE, cùng cart/bundle/số bộ/lựa chọn và còn hạn. Server resolve SKU thay thế từ resolution cùng rule hiện hành, tính lại giá theo mục 3.1; không dùng kết quả evaluateBundle cấu hình gốc để ghi đè lựa chọn khách đã chấp nhận. Giá/rule đổi thì trả 409 và yêu cầu resolution mới. S05 chỉ nhận mode PREORDER. Nếu combo đã ngừng bán/hết hiệu lực, không thể tạo resolution mới; khách có thể hủy preorder hoặc chọn combo khác, không tự gia hạn hiệu lực catalog.

#### Quy ước triển khai và ví dụ HTTP

Body dùng @Valid, @NotNull/@NotBlank/@Size/@Min theo field đã đặc tả. JSON number là BigDecimal cho tiền; không tin tổng giá client. `ApiResponse.data` null thì omitted, `errors` rỗng thì omitted, timestamp UTC luôn có. Message lỗi nghiệp vụ mô tả bằng tiếng Việt; enum mã lỗi phải implement BaseErrorCode. Lỗi filter phải cùng envelope với controller.

| Nhóm API | Operation ID | Content-Type / auth / validation bổ sung |
|---|---|---|
| B10 | replaceBundleSubstitutionRules | application/json + Manage; UUID path, expectedVersion>=0, rule distinct; giống lỗi B06 |
| C01 / C02 / C03 | searchBundleSkus / uploadBundleMedia / getBundleMedia | C02 multipart; C01/C03 không body; tất cả Manage; giới hạn file/query ở bảng trên |
| S01 / S02 / S03 | addBundleToCart / proposeBundleResolution / getBundleResolution | POST JSON + Idempotency-Key; GET không body; quyền/ownership Sales |
| S04 / S05 / S06 | acceptBundleResolution / createBundlePreorder / getBundlePreorder | acceptedTotal chỉ chứng minh khách đồng ý, server tính lại; resolution/UUID hợp lệ |
| S07 / S08 / S09 | confirmBundlePreorder / cancelBundlePreorder / reconfirmBundlePreorder | POST JSON + version + key; mode/ownership/trạng thái hợp lệ; không coi retry là chấp nhận giá mới |

```json
{"success":true,"code":"SUCCESS","message":"Đã tải ảnh.","data":{"assetId":"40000000-0000-4000-8000-000000000001","imageUrl":"https://example.invalid/banner.webp","status":"STAGED"},"timestamp":"2026-10-17T00:00:00Z"}
```

Ví dụ URL chỉ minh họa response; hệ thống chỉ trả URL đã được provider xác minh. S04 request ví dụ:

```json
{"expectedVersion":1,"mode":"PREORDER","replacements":[],"acceptedTotal":95000000.00}
```

Nullability: admin legacy cho phép packagePrice/validFrom/validUntil null, có publishBlockers; public chỉ hiển thị cấu hình đủ field. Giá lẻ không tính được thì originalTotal/saving/discount null và purchasable=false. Giá gói vượt tổng lẻ sau publish: preview 200, priceIssue=PACKAGE_EXCEEDS_RETAIL, saving/discount null. SKU inactive không bị xóa khỏi response: trả sellable=false. Đây là schema response thống nhất, không phải hai cách xử lý tùy controller.

### 5.2. Internal Synchronous Java Interface (module/api)

Mỗi interface nằm trong `com.aquasuite.ims.module.<owner>.api`, DTO trong `api.dto`, implementation trong internal. Caller không import entity/repository/internal của provider. Mọi input/output bên dưới không null trừ trường ghi rõ nullable. Tên API là proposal, không phải API đã tồn tại.

#### CurrentActorApi.requireActor — proposal tích hợp sau Phase 1

| Contract Attribute | Specification Value |
|---|---|
| Contract Interface & Method | CurrentActorApi.requireActor |
| Caller / Consumer → Callee / Provider | catalog → iam |
| Input Parameters | String permission nonblank; từ security context đã verified |
| Return Value | ActorDto(Long userId, Set<String> permissions) |
| Error Conditions | 401 AUTH_001 thiếu/inactive User; 403 AUTH_002 thiếu quyền |
| Authorization Context | Không tin actor từ body; không gọi mock fallback |
| Transaction Behavior | readOnly REQUIRED, chỉ đọc User |
| Versioning & Compatibility | ActorDto trong iam.api.dto; thêm field optional tương thích, đổi claim/required permission phải review consumer |

#### InventoryQueryApi.getAvailability

| Contract Attribute | Specification Value |
|---|---|
| Contract Interface & Method | InventoryQueryApi.getAvailability |
| Caller / Consumer → Callee / Provider | catalog → inventory |
| Input Parameters | Set<UUID> distinct, tối đa 10,000; kho từ cấu hình server |
| Return Value | Map<UUID, InventoryAvailabilityDto>; thiếu key là UNKNOWN, giá trị availableQty BigDecimal |
| Error Conditions | 400 SYS_002 input sai; lỗi dependency map 503 CATALOG_BUNDLE_005 ở consumer giao dịch; preview trả UNKNOWN |
| Authorization Context | Context public-read hoặc Owner đã xác minh; không public HTTP số tồn kho |
| Transaction Behavior | readOnly REQUIRED; không lock ghi, không reserve |
| Versioning & Compatibility | DTO trong inventory.api.dto; giữ missing khác zero; không delegate facade cũ chọn tùy dòng kho |

#### AuditApi.append — thiết kế lịch sử, provider Java đã được bỏ trong revision 0.13.0

| Contract Attribute | Specification Value |
|---|---|
| Contract Interface & Method | AuditApi.append |
| Caller / Consumer → Callee / Provider | catalog/sales → audit |
| Input Parameters | AuditCommand(actorId Long, action, aggregateId UUID, before/after JSON, occurredAt); before null khi tạo |
| Return Value | UUID auditEntryId |
| Error Conditions | 400 SYS_003 command sai; lỗi lưu 500 SYS_001 và rollback caller |
| Authorization Context | Chỉ service nội bộ đã kiểm actor/quyền; payload không có credential |
| Transaction Behavior | MANDATORY, cùng datasource/transaction manager; không AFTER_COMMIT |
| Versioning & Compatibility | AuditCommand trong audit.api.dto; đổi schema audit phải version/review; UUID tạo mới cho từng mutation thật |

#### CatalogBundleApi.evaluateBundle

| Contract Attribute | Specification Value |
|---|---|
| Contract Interface & Method | CatalogBundleApi.evaluateBundle |
| Caller / Consumer → Callee / Provider | sales → catalog |
| Input Parameters | BundleEvaluationRequest: bundleId, quantity>=1, selections, expectedVersion bắt buộc |
| Return Value | BundleEvaluationDto: SKU/qty/giá/version/evaluatedAt/availability |
| Error Conditions | SYS_004 404; CATALOG_BUNDLE_001 400 selection; _003 409 stale; _005 503 khi không đọc đủ tồn |
| Authorization Context | Sales kiểm quyền giỏ/đơn; catalog kiểm visibility; không nhận cờ bỏ qua tồn từ HTTP |
| Transaction Behavior | readOnly REQUIRED; không giữ hàng; sales revalidate trước xác nhận |
| Versioning & Compatibility | DTO trong catalog.api.dto; không dùng entity; thay required field/giá cần consumer review |

#### CatalogBundleApi.getShortageOptions

| Contract Attribute | Specification Value |
|---|---|
| Contract Interface & Method | CatalogBundleApi.getShortageOptions |
| Caller / Consumer → Callee / Provider | sales → catalog |
| Input Parameters | Bundle ID/version, selections, quantity; context reprice preorder cho phép không còn shortage |
| Return Value | ShortageOptionsDto: rules, SKU nguồn/đích, priceAdjustment, preorderAllowed |
| Error Conditions | Như evaluateBundle; không thấy candidate trả list rỗng, không giả còn hàng |
| Authorization Context | Cùng context evaluateBundle; chỉ rule Owner cho phép |
| Transaction Behavior | readOnly REQUIRED |
| Versioning & Compatibility | DTO trong catalog.api.dto; option whitelist không tự là substitution rule |

#### InventoryReservationApi.reserveAll

| Contract Attribute | Specification Value |
|---|---|
| Contract Interface & Method | InventoryReservationApi.reserveAll |
| Caller / Consumer → Callee / Provider | sales → inventory |
| Input Parameters | businessReference UUID, requestKey, expiresAt, lines SKU/qty>0 |
| Return Value | ReservationDto(id,status,expiresAt); cùng key/body trả cùng kết quả |
| Error Conditions | Thiếu tồn map 409 SALES_BUNDLE_003; key khác body 409 SALES_BUNDLE_005; dependency 503 SALES_BUNDLE_006 |
| Authorization Context | Chỉ Sales đã kiểm ownership; không HTTP gọi trực tiếp |
| Transaction Behavior | REQUIRED cùng DB theo D06-T; khóa SKU/kho theo thứ tự; giữ đủ hoặc rollback |
| Versioning & Compatibility | DTO trong inventory.api.dto; mã lỗi provider độc lập, Sales map HTTP; đổi atomicity phải review |

#### InventoryReservationApi.release

| Contract Attribute | Specification Value |
|---|---|
| Contract Interface & Method | InventoryReservationApi.release |
| Caller / Consumer → Callee / Provider | sales/system → inventory |
| Input Parameters | reservationId UUID, reason, requestKey |
| Return Value | ReservationDto; RELEASED/EXPIRED retry no-op |
| Error Conditions | CONSUMED từ chối 409; missing 404; dependency 503 qua caller mapping |
| Authorization Context | Chỉ cancellation/expiry đã xác minh; không nhả reservation khách khác |
| Transaction Behavior | REQUIRED theo D06-T; lock ledger và inventory, giảm reserved đúng một lần |
| Versioning & Compatibility | DTO như reserveAll; thay lifecycle phải review Sales/Fulfillment |

### 5.3. Domain Events (module/api/event)

| Thuộc tính | Quyết định trong UC13 |
|---|---|
| Event mới cho publish/evaluate | Không có; publish không trừ tồn; không tạo event chỉ để đủ template |
| Audit | Ghi đồng bộ cùng transaction qua public API; không dùng AFTER_COMMIT đơn thuần |
| Publisher / consumer / payload / timing / phase / key / retry / pending | NOT_APPLICABLE cho thiết kế không dùng event mới |
| Event khi chọn SAGA hoặc thông báo hàng về | Chưa đặc tả, chặn phần triển khai liên quan đến khi D06-T được duyệt và bổ sung record trong api.event, eventId, outbox/retry/replay, failure observability và pending state |
| Listener nếu được bổ sung | AFTER_COMMIT không là durable queue; listener ghi DB cần REQUIRES_NEW, dedup và phục hồi; không mặc định @Async |

## 6. Concurrency, Transactions, and Failure Scenarios

### Core quản trị — giới hạn transaction

Core create/update/status/delete ghi Product + BundleConfiguration + BundleItem cùng transaction; AuditApi deferred đến khi tích hợp IAM. Header dùng @Version và phải được cập nhật cả khi chỉ sửa Product.status hoặc items. Hai request update/delete/status cùng expectedVersion chỉ một thắng; loser 409 khi resource còn tồn tại, hoặc 404 cho update/status nếu DELETE đã thắng và resource bị ẩn theo B06/B12. Không để lại mutation một phần. Không chỉ đặt @Version trên header mà không làm header dirty. Đọc danh sách/detail theo snapshot DB read-only, giá SKU là thông tin hiện tại, không cam kết giá bán.

Không ghi Inventory/Media/Sales trong core; vì thế kiểm thử rollback chỉ cần các bảng catalog cùng transaction manager. Điều này không tự đóng D06-T của luồng giữ hàng tương lai. DELETE giữ reference vật lý nên không phá FK/snapshot cũ; import catalog cũ nếu có khả năng sửa Product BUNDLE phải được kiểm tra và chặn bypass version bằng revision riêng trước khi cho chạy song song.

Luồng Excel import hiện có được đưa vào file scope phase 1 để chặn cập nhật Product BUNDLE hoặc variant thuộc BUNDLE trước mutation, kể cả mã combo đã xóa. Import trả row error, không dùng import để bật lại combo hoặc sửa definition. Test hồi quy phải chứng minh NORMAL import vẫn hoạt động; đây là guard bảo toàn core, không triển khai quản lý combo qua Excel.


- **Tạo/sửa/publish:** product, cấu hình, thành phần và liên kết ảnh đã xác minh phải lưu nguyên tử trong transaction catalog. Lỗi một dòng phải rollback toàn bộ definition.
- **Sửa đồng thời:** optimistic version; hai request cùng version chỉ một thành công, request còn lại 409. Cần khóa/version bao trùm thay đổi thành phần, không chỉ header.
- **Giá/tồn thay đổi giữa preview và checkout:** sales yêu cầu đánh giá lại, xác nhận giá theo policy D07; inventory giữ đủ toàn bộ SKU hoặc rollback. Không triển khai vòng lặp reserve riêng từng SKU rồi tuyên bố atomicity.
- **Cloudinary:** upload ngoài DB transaction; DB thất bại sau upload phải có cơ chế cleanup/retry hoặc asset staging. Không rollback DB bằng cách xóa asset đang được combo khác dùng.
- **Inventory lỗi:** browse hiển thị UNKNOWN; giao dịch cần xác thực tồn không được tiếp tục như còn hàng. Retry read có giới hạn; reservation cần idempotency key do contract sales/inventory quy định.
- **Publish lặp/retry:** kết quả xác định cho cùng trạng thái/version; tránh tạo duplicate combo khi client retry create bằng idempotency policy được duyệt hoặc unique product code.
- **Snapshot sales:** thành phần, số lượng, giá và giảm giá đã chấp nhận phải được lưu để sửa combo không làm sai lịch sử. Phân bổ discount cho từng dòng, thuế và rounding thuộc D07.

### Failure and Recovery — media

| Lifecycle media | Hành vi và phục hồi |
|---|---|
| Upload | Ghi MediaAsset UPLOADING với UUID/publicId dự kiến trước gọi provider; sau upload xác minh metadata và ghi STAGED. Nếu provider/DB lỗi, giữ dấu vết để đối soát, không trả asset thành công |
| Gắn ảnh | Lock asset row; UPLOADING/DELETE_PENDING/DELETED không được gắn; STAGED → ATTACHED; ghi image/audit cùng transaction; rollback không làm mất asset STAGED |
| Thay ảnh | Xóa reference ProductImage của combo, không gọi xóa Cloudinary trong transaction. Khi không còn reference, asset trở về STAGED và ghi detachedAt |
| Cleanup | Job mỗi giờ chọn UPLOADING/STAGED không có reference và quá 24h; lock rồi đổi DELETE_PENDING trong DB, commit trước gọi xóa theo publicId; API attach phải từ chối trạng thái này. Provider xác nhận xóa/không tồn tại thì DELETED; lỗi giữ pending và retry lần sau, log asset ID, không log secret |
| Lỗi/crash | DELETE_PENDING retry an toàn; publicId xác định trước upload giúp đối soát upload thành công nhưng mất response. Không xóa asset ATTACHED; race attach/cleanup được test bằng hai transaction |

### Transaction Boundaries — giao dịch

| Tình huống | Xử lý bắt buộc |
|---|---|
| Mất response sau commit | Retry bằng cùng Idempotency-Key trả lại tài nguyên/kết quả trước, không tạo giỏ/đơn/reservation lần hai |
| Hai người mua phần tồn cuối | Lock/conditional update tồn cùng transaction; chỉ một giao dịch giữ đủ bộ, giao dịch kia rollback hoàn toàn |
| Đổi giá/rule sau khách đồng ý | Đánh giá lại trước thêm giỏ hoặc xác nhận; cần resolution mới khi kết quả đổi; không thu thêm ngầm |
| Thay combo sau đã xác nhận đơn | Snapshot và giá đơn giữ nguyên; không sửa lịch sử do catalog unpublish hoặc edit |
| Job nhả reservation quá hạn | Xử lý từng reservation ACTIVE đã quá hạn cùng transaction với trạng thái đơn/preorder; khóa cùng thứ tự như hủy/xác nhận; retry không nhả hai lần |
| Audit hoặc DB ghi thất bại | Rollback giao dịch, kể cả reservedQty; không báo thành công rồi mới ghi audit bằng listener dễ mất |

Với phương án chung DB/transaction manager đã được chứng minh, luồng này không cần Domain Event mới để bảo đảm tính đúng của giữ/nhả tồn. Nếu bổ sung thông báo hàng về sau này, phải có outbox/retry và contract event riêng; không thay thế các kiểm tra bắt buộc khi xác nhận.

### Concurrency Controls — bàn giao reservation

Reservation CONSUMED không được job expiry nhả lại. Giao hàng/xuất kho nằm ngoài UC13, nhưng D06-E phải chốt contract consume với Inventory/Fulfillment, thời điểm giảm onHand/reserved, dedup và test consume-vs-cancel-vs-expire trước nghiệm thu giao dịch. Chưa có contract này thì không coi vòng đời reservation hoàn tất. Khi không chung transaction manager, dừng phần giữ hàng để thiết kế lại SAGA theo D06-T.

### Cấu hình runtime và triển khai

| Quy định | Thiết kế tuân thủ |
|---|---|
| Profile | Giữ bốn file common/dev/test/prod (common không là profile được kích hoạt); không thêm application-phase1-it.yml. Maven profile phase1-it chỉ chọn test/build, không phải Spring profile |
| Kích hoạt | SPRING_PROFILES_ACTIVE do môi trường cấp; application.yml không hardcode active profile. Test dùng @ActiveProfiles("test") và DynamicPropertySource để cấp container URL/credential tạm |
| Marker | aquasuite.config.environment chỉ phục vụ chẩn đoán/test ConfigData; không dùng rẽ nhánh pricing/security/stock |
| Cấu hình nghiệp vụ | Kho, giới hạn upload, timeout và cleanup bind qua properties class; auth issuer/audience/JWK và Cloudinary secret lấy từ môi trường; không tự đọc .env |
| Secret | Không credential thật/fallback rỗng; cấu hình bắt buộc thiếu thì fail startup khi feature bật; không dump environment; prod dùng secret manager/env |
| Dependency | Giữ Boot 3.5.16/Java21; Framework/Security/Jackson/JUnit theo BOM, không override version lõi và không thêm Boot4 test starters |
| Migration | Ghi chú FEATURE-004 là lịch sử baseline; migration schema UC13 là script/runbook riêng. Kiểm DB test và smoke mới, không tái sử dụng PASS lịch sử cho feature mới |

## 7. Acceptance Criteria (AC)

| AC / yêu cầu | Kết quả phải đạt | Trạng thái |
|---|---|---|
| AC01 / R02 | Owner có quyền tạo definition hợp lệ; product là BUNDLE, thành phần đúng SKU/qty; lỗi bất kỳ dòng nào không để lại definition một phần. | Chưa kiểm thử |
| AC02 / R03 | Dữ liệu ví dụ SRS trả tổng 110,000,000; giá gói 95,000,000; tiết kiệm 15,000,000 và hiển thị 13.6% theo rounding đã duyệt. | Chưa kiểm thử |
| AC03 / R01, R05 | Publish hợp lệ trả MSG44; danh sách/detail cung cấp đầy đủ thông tin, không lộ draft hoặc combo ngoài hiệu lực trên public API. | Chưa kiểm thử |
| AC04 / R04 | Kiểm thử trước/bằng/sau các mốc hiệu lực theo timezone và quy tắc biên đã duyệt. | Chưa kiểm thử |
| AC05 / R08 | Thiếu SKU bắt buộc trả trạng thái thiếu một phần và danh sách SKU; dependency lỗi trả UNKNOWN khi browse, không xác nhận có hàng. | Chưa kiểm thử |
| AC06 / R06 | Lựa chọn SKU chỉ thuộc nhóm option được cho phép; thay thế không hợp lệ bị từ chối; giá phản ánh lựa chọn theo D05. | Chưa kiểm thử |
| AC07 / R07, R09 | Sales nhận đúng toàn bộ SKU × số bộ và giá gói; cart, POS, quotation, checkout có kiểm chứng tích hợp. Catalog preview riêng chưa đủ PASS AC này. | Chưa kiểm thử |
| AC08 | Guest/Customer/Sales không thể quản lý combo; quyền xem tuân theo D01; public DTO không lộ giá vốn. | Chưa kiểm thử |
| AC09 | Sửa/publish bằng revision cũ nhận 409; không ghi đè thay đổi mới hoặc cập nhật một phần. | Chưa kiểm thử |
| AC10 | SKU trùng, quantity 0/âm, nesting, SKU không tồn tại và khoảng ngày/giá sai bị từ chối theo quy tắc đã duyệt. | Chưa kiểm thử |
| AC11 | Thay đổi giá/thành phần có audit; lịch sử đơn/báo giá cũ không đổi khi combo được sửa/unpublish. | Chưa kiểm thử |
| AC12 | Banner gắn đúng combo; lỗi media/DB không publish dữ liệu dở dang; không sử dụng asset trái quyền. | Chưa kiểm thử |
| AC13 / R02, R08 | Owner tìm được SKU hợp lệ để cấu hình; thiếu SKU có lựa chọn thay thế được phép hoặc luồng preorder theo quyết định D05/D06. Chỉ hiện chữ “thiếu tồn” chưa đáp ứng phần xử lý thay thế/đặt trước của R08. | Chưa kiểm thử |
| AC14 | Mỗi API-B01..B12, C01..C03 và S01..S09 đáp ứng contract request/response/status, paging, lifecycle và retry đã duyệt; kết quả preview không được hiểu là cart mutation hoặc reservation. | Chưa kiểm thử |
| AC15 | Đúng cấu hình dev/test/prod, không có secret/profile mặc định hoặc dùng marker làm logic; dependency phù hợp Boot 3.5.16; migration bảo toàn dữ liệu cũ. | Chưa kiểm thử |
| AC16 | Owner xóa mềm combo, dữ liệu/code/audit giữ nguyên; list/detail không hiển thị đã xóa; retry và xung đột version đúng; không ảnh hưởng SKU/tồn/snapshot. | Chưa kiểm thử |
| AC17 | Owner bật/tắt ACTIVE/INACTIVE nguyên tử với audit/version; trạng thái nội bộ không tự publish; request không quyền bị chặn. | Chưa kiểm thử |

## 8. AC-to-Test Verification Matrix

| AC ID | Verification Method | Test Scope / Target | Expected Result | Pass/Fail Criteria |
|---|---|---|---|---|
| AC01 | Service + PostgreSQL integration | API/service/DB theo AC và mapping trong plan | Commit đầy đủ hoặc rollback toàn bộ | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC02 | Unit pricing + API integration | API/service/DB theo AC và mapping trong plan | Tiền chính xác; không sai số float; rule rounding nhất quán | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC03-04 | Controller + repository + clock cố định | API/service/DB theo AC và mapping trong plan | Filter publication/hiệu lực đúng, paging ổn định | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC05 | Inventory contract test: đủ/thiếu/missing/error | API/service/DB theo AC và mapping trong plan | Availability chính xác, không biến UNKNOWN thành IN_STOCK | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC06 | Selection validation/service | API/service/DB theo AC và mapping trong plan | SKU ngoài nhóm bị chặn; combo evaluation đúng | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC07 | Consumer/provider integration với sales | API/service/DB theo AC và mapping trong plan | Mọi kênh dùng cùng contract; snapshot và discount đúng; không giữ tồn một phần | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC08 | Security filter + controller tests | API/service/DB theo AC và mapping trong plan | 401/403 đúng; public read theo D01; JSON không có costPrice | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC09 | Integration với hai transaction cạnh tranh | API/service/DB theo AC và mapping trong plan | Một request thắng, request stale 409 | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC10 | Parameterized validation + DB constraints | API/service/DB theo AC và mapping trong plan | Không auto-correct invalid quantity; duplicate/invalid references bị chặn | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC11 | Audit integration + immutable sales snapshot | API/service/DB theo AC và mapping trong plan | Có before/after; dữ liệu giao dịch cũ không đổi | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC12 | Media adapter failure tests | API/service/DB theo AC và mapping trong plan | Không publish dở dang; cleanup không xóa asset dùng chung | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC13 | SKU search + option/preorder integration | API/service/DB theo AC và mapping trong plan | SKU lookup có thật; hướng xử lý thiếu tồn theo quyết định đã duyệt, không chỉ có nhãn UI | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC14 | Controller contract + lifecycle/retry tests cho B01-B12/C01-C03/S01-S09 | API/service/DB theo AC và mapping trong plan | Schema/field/nullability/status đúng; version/no-op và pagination nhất quán | PASS chỉ khi assertion đạt, không lỗi hoặc skipped; thiếu môi trường ghi NOT_RUN |
| AC15 | ConfigData + migration + dependency review | 4 profile chuẩn, env precedence, DB upgrade, effective POM | Bảo toàn dữ liệu và baseline; không lộ secret | Chạy độc lập với DB production; ghi rõ report và số test |
| AC16 | PostgreSQL + HTTP + concurrency | B11 | Tombstone, code unique, không hard-delete và loser rollback | Cùng version update/delete chỉ một commit; query list/detail không thấy đã xóa |
| AC17 | Service/HTTP/security + PostgreSQL | B12 | Active/inactive/no-op/version và audit đúng | Không thay publication/tồn; thiếu quyền 403, deleted 404 |

AC01-AC17 mô tả đích nghiệm thu toàn UC; trạng thái “Chưa kiểm thử” ở trên là chưa nghiệm thu toàn bộ AC, không phủ nhận bằng chứng core đã có. Phase 1 kiểm phần CRUD của AC01, AC02, AC09, AC10, lookup của AC13, core contract của AC14, AC15 và phần xóa/status của AC16/17. AC08 và mọi phần actor/quyền/audit của AC01/11/16/17 deferred; không yêu cầu security test, audit write hoặc external IAM smoke cho core. AC03-07/12 và phần còn lại của AC13/14 là Phase 2/3. Plan theo dõi từng phần và bằng chứng, không đánh dấu toàn UC PASS từ kết quả core.

### Kiểm chứng tích hợp bổ sung

- Chỉ đề nghị rule thay thế hợp lệ; chưa đồng ý không đổi SKU; giá/rule đổi yêu cầu xác nhận lại.
- Ghi toàn bộ dòng giỏ hoặc rollback; cùng idempotency key/body trả kết quả cũ, khác body trả 409.
- Preorder đúng state machine và D06-P; NEEDS_RECONFIRMATION được lưu bền vững; thiếu/UNKNOWN không tạo đơn.
- Hai transaction tranh tồn cuối; lỗi SKU cuối, ghi đơn hoặc audit phải rollback toàn bộ theo D06-T. Hủy/hết hạn/retry không giữ hoặc nhả hai lần.
- Cart/POS/quotation/checkout dùng chung cách tính và snapshot; tổng dòng khớp giá gói; lịch sử đã chấp nhận không đổi theo Catalog.
- Chặn truy cập tài nguyên khách khác; S01-S09 đúng status/envelope/version; mất response vẫn truy hồi kết quả.
- Giá giỏ 5m → 5.5m, giá SKU đổi nhưng bundle version không đổi, TTL đúng biên và unpublish/hết hiệu lực với từng loại giao dịch phải được kiểm tra theo policy đã duyệt.

Đây là tiêu chí kiểm chứng toàn bộ tính năng; kết quả hiện NOT_RUN. Plan quy định thứ tự thực hiện và kiểm chứng từng phần.

Kịch bản bắt buộc thêm: hàng về đủ nhưng giá đổi → tạo resolution xác nhận lại → S09 → S07; không yêu cầu thiếu hàng lần nữa. Reservation consume/expiry/cancel không xử lý hai lần; vòng đời chưa có contract thì ghi BLOCKED, không dùng mock thay bằng chứng tích hợp.

## Phụ lục A. Decision Register và điều kiện review

| ID | Quyết định cần chốt | Đề xuất để review | Người cần quyết định |
|---|---|---|---|
| D01 | Quyền Guest/Customer so với dòng bundle trong permission matrix | Public browse combo hiệu lực; manage chỉ Owner; phân biệt chức năng nội bộ | Product Owner + IAM owner |
| D02 | Lưu giá gói, publication; qty nguyên; duplicate/nesting; mã và category combo | Bảng cấu hình riêng; giữ product BUNDLE; mã bất biến; integer qty; reject duplicate/nesting | Catalog owner + DB reviewer |
| D03 | Giá bằng 0/cao hơn tổng; rounding; thời điểm lấy giá lẻ | Giá gói dương; quy tắc giá và làm tròn ở 3.1; cần phê duyệt | Product Owner + finance/sales owner |
| D04 | Timezone, hiệu lực, null dates và publish trước ngày bắt đầu | Lưu Instant/UTC, hiển thị Asia/Ho_Chi_Minh; khoảng [from, until) | Product Owner |
| D05 | Option/substitute hợp lệ, phụ phí và cấu trúc nhóm | D05-O: xác nhận nhóm biến thể do Owner cấu hình; D05-P: chọn giá cố định/phụ phí/cấm khác giá; D05-S: rules thay thế khi thiếu hàng. Tất cả PENDING | Product Owner + catalog owner |
| D06 | Stock policy, preorder, thời điểm giữ tồn | Browse/evaluate chỉ đọc; D06-T: chứng minh transaction boundary; D06-P: chốt chờ cả combo hay chỉ SKU thiếu và giữ phần còn hàng; D06-E: hạn/kho/hủy. Xem Phụ lục A.2; tất cả PENDING | Product Owner + inventory/sales owner |
| D07 | Snapshot, phân bổ discount, thuế, reprice và cart grouping | D07-L: khóa giá/reprice/TTL theo trạng thái; D07-V: unpublish/hết hạn theo loại giao dịch; D07-F: thuế/phân bổ. Snapshot không tự khóa giá; tất cả PENDING | Sales + finance owner |
| D08 | Edit published combo, optimistic concurrency và API version | Unpublish trước edit; version toàn aggregate; retry version cũ 409; revalidate khi publish/checkout | Tech lead |
| D09 | Audit bền vững và Cloudinary asset lifecycle | Ghi audit có bảo đảm; media staging/cleanup policy rõ | Audit/media owner |
| D10 | Phạm vi integration sales, SKU lookup UC-12, migration tool và quyền IAM thực | Chốt provider/endpoint cho lookup và consumer sales; chia lane rõ, không tuyên bố UC hoàn tất khi chỉ xong catalog | Ticket owner + tech lead |

### A.1 Điều kiện nghiệm thu đầy đủ

Toàn bộ UC-13 cần đáp ứng R01-R09 và AC01-AC17, bao gồm tích hợp cart, POS, quotation, checkout, xử lý thiếu hàng và bảo toàn lịch sử giao dịch. Quyết định còn mở phải được chốt trước khi triển khai phần bị ảnh hưởng. Thứ tự triển khai, điều kiện vào/ra và phạm vi nghiệm thu từng phase được quy định trong plan.

### A.2 Quyết định chi tiết về transaction, preorder, giá và hiệu lực

Tất cả quyết định dưới đây là PENDING. Các API/state machine ở mục 5.1 mô tả **phương án** chờ cả bộ, đánh giá lại giá và chung DB/transaction manager; không phải yêu cầu được SRS xác nhận. Khi chọn phương án khác phải cập nhật API/schema/state machine/test và review lại trước code.

| ID | Cần chốt và bằng chứng | Người quyết định |
|---|---|---|
| D06-T | Kiểm tra datasource, transaction manager, propagation và đường gọi Sales → Inventory thực tế. Phương án đồng bộ chỉ dùng khi mọi ghi order/reservation/reservedQty/audit cùng DB transaction; test lỗi dòng cuối và lỗi ghi order phải rollback hết. `@Transactional` không bảo đảm atomicity giữa service/DB độc lập. Nếu không chung transaction phải thiết kế pending state, saga/compensation, durable outbox/retry riêng rồi review lại | Tech lead + Sales/Inventory |
| D06-P | Chọn chờ toàn combo, không giữ SKU đang có (phương án mục 5.1), hay chỉ preorder SKU thiếu và giữ phần còn hàng. Phương án sau cần thiết kế partial reservation, expiry, hủy và hoàn tác tương ứng; không triển khai bằng cách đổi một flag | Product Owner + Sales/Inventory |
| D06-E | Chọn kho, hạn resolution/preorder/reservation, hủy đơn và nhả hàng; đối soát reservedQty hiện có trước ledger mới | Sales/Inventory |
| D07-L | Chốt khóa giá hay đánh giá lại, thời điểm chấp nhận và TTL theo từng trạng thái ở bảng dưới; snapshot là bằng chứng lịch sử, không tự cam kết giá | Product Owner + Sales/Finance |
| D07-V | Chốt xử lý unpublish/hết hiệu lực riêng cho cart, báo giá đã gửi, preorder và đơn đã xác nhận | Product Owner + Sales |
| D07-F | Chốt giá gồm/chưa gồm thuế và cách phân bổ/làm tròn, tổng dòng luôn bằng tổng gói | Sales/Finance |

## Phụ lục B. Revision notes

| Version | Nội dung |
|---|---|
| 0.8.0 | Giữ đủ UC13; bổ sung B11/B12, lifecycle xóa mềm/bật tắt, Core DTO và AC16/17; plan phase 1 chỉ core quản trị, các phần còn lại ở roadmap |
| 0.7.0 | Viết lại theo tám mục template; chuyển auth/schema/giá/media vào đúng mục; đủ thuộc tính Java contracts; cấu hình bốn profile và baseline Boot 3.5.16; sửa đường xác nhận lại giá khi đã đủ hàng; giữ decision chưa duyệt |
| 0.6.0 | Chi tiết thiết kế combo cố định và dependency hỗ trợ |
| 0.5.0 | Một spec toàn UC13; ba phase chỉ trong plan |

Approval phase 1 nằm trong review artifact đúng version/hash. Tài liệu không tự cấp quyền code; phase 2–3 chưa được duyệt, archive/handoff lịch sử được giữ nguyên.

## Revision 0.9.0 — Core độc lập IAM

Yêu cầu người dùng mới thay thế ràng buộc actor/JWT/permission và audit trong core của bản 0.8.1. Phần nghiệp vụ đầy đủ, actors và hợp đồng tích hợp tương lai vẫn được giữ; phân bổ ba phase nằm trong spec và đầu việc/tiến độ nằm trong plan. Approval/hash 0.8.1 không áp dụng cho thay đổi 0.9.0; không tự tạo approval mới.

B06 PUT hợp lệ vẫn tăng version kể cả cùng definition; no-op chỉ B11/B12. Core dùng timestamp BundleConfiguration UTC, giữ giới hạn tổng giá và các validation đã có. AC08 và phần audit của AC11/AC16/AC17 chưa nghiệm thu cho core, chờ feature IAM/Audit chung. Không dùng external IAM smoke làm blocker cho CRUD hiện tại.

## Revision 0.9.1 — Iteration 1, ưu tiên core

Người dùng xác nhận phạm vi sau rà soát: Phase 1 quản trị core không Auth/Audit; Phase 2 đăng bán/lựa chọn; Phase 3 giao dịch/thiếu hàng. Bổ sung luồng chính/thay thế, ánh xạ phase trong spec, sửa CURRENT theo workspace và bỏ điều kiện JWT/audit còn sót trong Core DTO/B11/B12. Thiết kế đầy đủ vẫn giữ để review sau, không kéo vào Iteration 1.

Đợt 0.9.1 là đồng bộ tài liệu, không đổi mã nguồn/API payload/schema. Scope confirmation không phải technical review APPROVED hoặc Final Gate. Đợt code/test tiếp theo theo plan 0.9.2 có [implementation handoff hiện hành](../../.agent/handoffs/SCRUM14-UC13-phase1-verification.md). Các handoff cũ đã được bỏ theo yêu cầu người dùng ngày 2026-10-19; chỉ giữ bản mới nhất.

### Tiến độ core sau đối chiếu code — plan 0.9.2

Ngày 2026-10-17, theo yêu cầu trực tiếp của người dùng, code được đối chiếu Core DTO/B03-B06/B11/B12/C01. Đã sửa ưu tiên 404 của update/status với combo DELETED; DELETE giữ kiểm stale/no-op tombstone. Thêm 12 test case hồi quy về lifecycle, rollback thay toàn bộ items, SKU/category không hợp lệ, PUBLISHED và lọc/phân trang. Không đổi hợp đồng công khai hoặc SQL schema; spec giữ version 0.9.1.

Full verify sau sửa: **35 unit + 37 integration = 72 test case PASS trong một lần chạy, 0 failures/errors/skipped, BUILD SUCCESS**. Log `uc13-iteration1-final-verify.log`; lệnh/môi trường/test-to-AC trong handoff hiện hành. Các subset core đã có bằng chứng mới và sẵn sàng review; chưa nghiệm thu toàn bộ AC hoặc UC-13. Auth/Audit và Phase 2/3 vẫn deferred; chưa deploy/live DB hoặc Final Gate.

### Refactor nội bộ plan 0.9.3 — 2026-10-20

Theo yêu cầu người dùng, chuyển BundleCoreMapper sang MapStruct, tách BundleDetailAssembler, gộp controller CRUD/lookup và dùng chung BundleItemRequest/BundleJsonDeserializers. Giữ DTO create/update và summary/detail riêng theo validation/response shape; giữ rule 400 cho query lạ/lặp qua validateQueryParameters. Không đổi HTTP/JSON/schema nên spec vẫn 0.9.1. Clean verify đạt **47 unit + 38 integration = 85 PASS**, không lỗi/skipped; JAR có generated mapper và không còn controller SKU cũ. Log `uc13-mapstruct-clean-verify.log`, chi tiết trong handoff hiện hành. Chưa stage/commit/push hoặc Final Gate.

### JavaDoc và gộp service plan 0.9.4 — 2026-10-20

Bổ sung comment tiếng Việt về tác dụng/caller/luồng của controller, service, assembler, mapper và DTO combo. SKU lookup chuyển vào BundleAdminService.searchSkus để cùng CRUD phục vụ một màn hình; pricing và helper ghép response giữ riêng. HTTP/JSON, lookup read-only, lọc SKU trước phân trang, transaction/version và phạm vi Phase 1 không đổi. Clean verify đạt **85/85 PASS** sau gộp; log `uc13-service-comments-verify.log`, JAR không còn service/controller SKU cũ. Không thêm test chỉ để kiểm comment; chưa commit/push hoặc Final Gate.

## Revision 0.10.0 - Phase 2 user-confirmed scope

This revision supersedes conflicting P2 proposals above: D05-P = C, options at the same current sellingPrice as their default SKU. D06-read = the existing single warehouse. User deferred Auth/Audit and media; banner/asset/actor are not publish prerequisites for this revision. No fake providers. Plan 0.10.0 tracks implementation; direct authorization is not independent technical approval.

Create/update add optional validFrom/validUntil (Instant with offset) and componentGroups (omitted/null = []). Core drafts may have no dates; otherwise both dates are required and from < until. Publish requires dates and until > now, may occur before from; public visibility is ACTIVE/PUBLISHED and from <= now < until (UTC). Each group has groupId/defaultVariantId/allowedVariantIds (distinct, contains default). Default belongs to items, at most one group per default. All options must be NORMAL ACTIVE and have the same price as default. Revalidate at save/publish; selected replacements must still match current default price at evaluation. Never add surcharges or silently change package price. PUT replaces dates/groups; unpublish before editing PUBLISHED.

GET /api/v1/catalog/bundles supports page,size,keyword and returns BundlePage<BundleResponse>, filtering visibility before paging. GET /{bundleId} returns BundleResponse with dates/groups and no cost price; stock is read through POST /{bundleId}/evaluations. Non-visible IDs return 404. Public definition reads do not reserve or imply availability.

Evaluation body: quantity (integer 1..10000), expectedVersion (optional nonnegative long), selections [{groupId,variantId}] (omitted/null=[]). Unselected groups use defaults; replacements retain quantity. Duplicate groups, unknown groups, non-whitelisted choices, unequal-price replacements and duplicate final SKU return 400 CATALOG_BUNDLE_001; stale version returns 409 CATALOG_BUNDLE_003. Response BundleEvaluation: bundleId,bundleVersion,evaluatedAt,quantity,packagePrice,totalPackagePrice,originalTotal,savingAmount,discountPercent,priceIssue,availability,purchasable,missingVariantIds,items [{variantId,sku,name,requiredQty,availableQty}]. Monetary totals apply to requested quantity; packagePrice is per set, discountPercent unchanged. All multiplied totals must fit DECIMAL(15,2). Missing rows/dependency errors -> UNKNOWN, never purchasable. Known insufficient rows appear in missingVariantIds. No reservation. Retail drop/inactive components produce non-purchasable preview with priceIssue and null savings/discount.

B07/B08 keep the documented routes and accept required expectedVersion through BundleVersionRequest. Stale is checked before no-op; publish revalidates current definition and returns message MSG44. Unpublish DRAFT returns 409; inactive/expired bundles may be unpublished. Auth/Audit/media C02/C03 and Sales remain deferred, not satisfied by these endpoints.

InventoryAvailabilityApi.availableQuantities(Collection<UUID>): Catalog -> Inventory, input non-null SKU IDs; returns Map<UUID,BigDecimal> available=onHand-reserved for the single warehouse; absent rows omitted. REQUIRES_NEW/read-only isolates query failure from Catalog's read transaction. Runtime failures propagate to preview, which returns UNKNOWN and logs only bundle ID/failure class. No actor required in current scope. Additive public API in inventory/api; does not change legacy facade or expose entities. Groups use bundle_component_groups with allowed UUID[] whitelist, not specifications JSONB.

Phase 2 verification: clean verify BUILD SUCCESS at 2026-10-20 15:05:17 +07; 111 PASS (56 unit + 55 integration), zero failures/errors/skipped. See [Phase 2 handoff](../../.agent/handoffs/SCRUM14-UC13-phase2-verification.md). Subset ready for review; full UC-13, security/audit/media and Sales are not certified.


## Revision 0.11.0 - authentication integration

User chose incoming authentication v2 with UUID users and JWT required on every non-login/refresh endpoint, including catalog list/detail/evaluation/admin, inventory and password APIs. This overrides earlier public/no-token statements. Reuse the incoming JWT decoder/current-authority checks and Redis session revocation; no bypass/fallback actor. Authentication alone is the incoming non-auth API policy; no new catalog role/permission matrix or Audit write integration is implied. Media and Phase 3 remain deferred. Preserve existing combo behavior behind the security filter. Legacy User IDs migrate to deterministic UUIDs with mapping/archive; no data drop or automatic role assignment. Password recovery state is separated from the incoming User schema.

Authentication merge verified at 2026-10-20 17:56:28 +07: 251 PASS (186 Surefire + 65 Failsafe), zero failures/errors/skipped. JWT-backed combo/inventory/password access and populated UUID upgrade are tested. Business Audit writes, media and Phase 3 remain outside this integration. No independent approval, live migration or deployment claimed.
