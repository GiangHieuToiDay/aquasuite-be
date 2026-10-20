# Hướng dẫn code UC13 — Phase 1: Core quản lý combo

Đối chiếu spec 0.9.1 / plan 0.9.6, ngày 2026-10-20; thêm JavaDoc tác dụng/luồng và gộp CRUD/SKU lookup vào một service quản trị. [Spec đầy đủ và phân bổ phase](specs/SCRUM14-UC13-product-combos.md), [plan và tiến độ](../.agent/plans/SCRUM14-UC13.md), [implementation handoff hiện hành](../.agent/handoffs/SCRUM14-UC13-phase1-verification.md).

## Phạm vi và đường đi request

Core cung cấp danh sách/chi tiết/tạo/sửa/xóa mềm/bật tắt combo và tìm SKU. Request HTTP → BundleAdminController → BundleAdminService → repository PostgreSQL → assembler/MapStruct → DTO → ApiResponse. Controller gọi cùng một service cho `/bundles` và `/bundle-skus`; lookup giữ transaction read-only. Core không yêu cầu token, actor, permission hoặc biến CATALOG_AUTH_*.

## Các thành phần

| Thành phần | Trách nhiệm |
|---|---|
| BundleAdminController | HTTP quản lý combo và tìm SKU; validateQueryParameters chặn tham số lạ/lặp |
| BundleAdminService | Điều phối CRUD/lookup SKU, validation, transaction và version; dùng chung repository/mapper |
| BundlePricingService | BigDecimal, tổng giá lẻ, tiết kiệm và phần trăm giảm |
| BundleDetailAssembler | Tính giá/sellable từ dữ liệu đã truy vấn, gọi mapper tạo response |
| BundleCoreMapper | Interface MapStruct map field detail/summary/item/mutation/SKU, không chứa nghiệp vụ hoặc gọi repository |
| BundleCoreCreateRequest/UpdateRequest | Định nghĩa combo đầu vào; update thêm expectedVersion |
| BundleCoreStatusRequest | ACTIVE/INACTIVE và expectedVersion |
| BundleCoreSummary/Detail | Response danh sách/chi tiết |
| BundleCoreMutationResult | Kết quả status/delete và version mới |
| BundleItemRequest | variantId/quantity dùng chung cho create/update |
| BundleJsonDeserializers | StrictInteger/StrictLong/StrictMoney dùng chung |
| BundleConfiguration | Giá gói, trạng thái công khai, timestamp và @Version; dates/stockPolicy dành cho phần sau |

## Quy tắc đang thực thi

Tạo Product BUNDLE ACTIVE và config DRAFT cùng các BundleItem. Không tạo SKU combo hoặc tồn kho combo. SKU phải hợp lệ; quantity nguyên 1..10000, 1..100 dòng không trùng; giá dương, tối đa DECIMAL(15,2), không vượt tổng lẻ. Unknown JSON/query và số lượng dạng chuỗi/thập phân bị từ chối.

Update giữ mã combo, từ chối PUBLISHED, thay toàn bộ danh sách; giữ ID item nếu SKU vẫn tồn tại. Khóa configuration PESSIMISTIC_WRITE, kiểm expectedVersion, touch timestamp và flush để @Version tăng kể cả chỉ sửa child/status. Stale trả 409. Tạo/sửa/lưu child thất bại rollback toàn bộ.

Status chỉ đổi Product ACTIVE/INACTIVE. DELETE đặt DELETED, giữ mã/ID/items; list/get thông thường ẩn bản ghi đã xóa. Sau khi lấy lock, update/status loại DELETED trước kiểm version nên trả 404 cả khi version cũ. DELETE vẫn kiểm version trước no-op tombstone: stale 409, version hiện tại 200 không tăng version. Không restore/hard delete.

Giá đọc theo sellingPrice SKU hiện tại. PricingService trả priceIssue khi giá không hợp lệ; assembler chuyển kết quả qua MapStruct, không coi là cam kết mua/giữ tồn. Import guard chặn ghi đè combo từ luồng import cũ.

## Query validation và các DTO có field chung

Spring bind các query đã khai báo nhưng có thể bỏ qua tên query lạ hoặc lấy một giá trị khi query bị lặp. validateQueryParameters giữ contract 400 SYS_002 cho `?surprise=x` hoặc `?page=0&page=1`; mỗi endpoint khai báo danh sách tên được phép. Helper này là validation đầu vào, không phải Auth. Khi bỏ helper, phải thay contract và test tương ứng.

Create/update chung 7 field nhưng tách DTO vì create không nhận expectedVersion còn update bắt buộc có. Summary/detail chung 14 field nhưng list dùng summary để không tải JSON items/description của detail. Không gộp bằng inheritance/validation groups hoặc response đầy field nullable. MapStruct tự sinh chuyển detail → summary; phần chung thực sự của request là BundleItemRequest và các bộ đọc số strict.

MapStruct dùng componentModel Spring và unmappedTargetPolicy ERROR để build lỗi khi thiếu field target mới. Processor dùng chung compile/testCompile, thêm Lombok binding theo [hướng dẫn MapStruct](https://mapstruct.org/documentation/1.6/reference/html/#_lombok). Generated implementation nằm trong target, không đưa lên Git.

## Vì sao chỉ gộp một phần service?

BundleAdminService và SKU lookup cùng phục vụ màn hình quản lý, cùng repository/mapper nên đã gộp. Controller không cần điều phối hai bean nữa. BundlePricingService được gọi từ validation ghi và assembler đọc; giữ công thức giá ở đây giúp hai luồng dùng chung và kiểm thử không cần DB. BundleDetailAssembler chỉ là helper nhận dữ liệu đã đọc để ghép response, không sở hữu use case hoặc transaction riêng. Ở Phase 1 chỉ giữ một application service và các helper có nhiệm vụ cụ thể.

JavaDoc trước controller/service/DTO mô tả mục đích, caller và luồng xử lý. Các bước khóa/version, xóa mềm/no-op, lọc SKU trước phân trang, preview so với validate giá và strict JSON được chú thích tại method tương ứng.

## Dấu vết và Audit

Product và BundleConfiguration giữ timestamp; config giữ version; Product.status lưu tombstone. Đây là dấu vết gần nhất, không phải lịch sử mọi lần sửa. Core đã ngừng gọi AuditApi vì chưa xác thực người thao tác. Module/bảng audit_entries còn tồn tại để tương thích migration, không tạo bản ghi CRUD mới.

createdBy/updatedBy/deletedBy sẽ lấy từ IAM khi tích hợp; không dùng User giả. Nếu cần lưu từng phiên bản giá/thành phần, có thể bổ sung bảng BundleRevision/BundleRevisionItem ghi cùng transaction sau khi chốt yêu cầu; hiện chưa triển khai.

## Môi trường và kiểm chứng

Java 21, Spring Boot 3.5.16, PostgreSQL; xem [migration runbook](migrations/flyway.md). Flyway quản lý toàn bộ schema; không sửa migration đã áp dụng lên database dùng chung.

Chạy `.\mvnw.cmd clean -Pphase1-it verify`; integration tests cần Docker/PostgreSQL Testcontainers. Bộ test HTTP không gửi token; fixture không dựng JWT/JWK server. Với môi trường Windows này, dùng Mockito javaagent và PSModulePath theo [handoff hiện hành](../.agent/handoffs/SCRUM14-UC13-phase1-verification.md). Clean verify sau gộp service đạt 47 Surefire + 38 Failsafe = 85 test case, không lỗi/skipped, BUILD SUCCESS ngày 2026-10-20. Log: `uc13-service-comments-verify.log`. Các lần chạy 72/73 test trước refactor là lịch sử.

Public/publish/media/tồn/options/Sales/preorder vẫn thuộc phần triển khai sau trong plan.

Iteration 1 chỉ nghiệm thu core B03-B06 Core DTO, B11/B12 và C01. Ma trận quyền, audit và DTO đầy đủ trong spec là thiết kế tích hợp tương lai; không thêm JWT verifier, audit writer hoặc bảng lịch sử mới để hoàn thành Phase 1. Schema audit trong migration cũ được giữ nguyên, không tạo dependency runtime cho CRUD. Plan 0.9.6 giữ các contract/transaction/version cũ và sẵn sàng review; chưa có Final Gate.

## Database migrations (plan 0.9.6)

Flyway now owns schema changes: `db/migration/V1__Initial_legacy_schema.sql` creates base tables, V2 applies the existing combo schema/backfill. Integration tests use migrations followed by Hibernate `validate`, including inventory entity mappings. Existing databases need explicit baseline onboarding: [runbook](migrations/flyway.md). Flyway is the only migration mechanism.
