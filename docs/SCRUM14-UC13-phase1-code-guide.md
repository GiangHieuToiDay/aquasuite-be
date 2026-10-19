# Hướng dẫn code UC13 — Phase 1: Core quản lý combo

Đối chiếu spec 0.9.1 / plan 0.9.2, ngày 2026-10-17; core đã được sửa/kiểm chứng lại theo yêu cầu người dùng. [Spec đầy đủ và phân bổ phase](specs/SCRUM14-UC13-product-combos.md), [plan và tiến độ](../.agent/plans/SCRUM14-UC13.md), [implementation handoff hiện hành](../.agent/handoffs/SCRUM14-UC13-phase1-verification.md).

## Phạm vi và đường đi request

Core cung cấp danh sách/chi tiết/tạo/sửa/xóa mềm/bật tắt combo và tìm SKU. Request HTTP → BundleAdminController/BundleSkuController → BundleAdminService/BundleSkuQueryService → repository PostgreSQL → DTO → ApiResponse. Core hiện không yêu cầu token, actor, permission hoặc biến CATALOG_AUTH_*. Feature IAM chung sẽ cung cấp các khả năng đó sau.

## Các thành phần

| Thành phần | Trách nhiệm |
|---|---|
| BundleAdminController | HTTP quản lý combo, query validation và response |
| BundleSkuController | HTTP tìm SKU đủ điều kiện |
| BundleAdminService | Điều phối CRUD, validation, transaction và version |
| BundleSkuQueryService | Query SKU ACTIVE thuộc Product NORMAL ACTIVE |
| BundlePricingService | BigDecimal, tổng giá lẻ, tiết kiệm và phần trăm giảm |
| BundleCoreMapper | Ghép dữ liệu và tính response, không trả JPA entity |
| BundleCoreCreateRequest/UpdateRequest | Định nghĩa combo đầu vào; update thêm expectedVersion |
| BundleCoreStatusRequest | ACTIVE/INACTIVE và expectedVersion |
| BundleCoreSummary/Detail | Response danh sách/chi tiết |
| BundleCoreMutationResult | Kết quả status/delete và version mới |
| BundleConfiguration | Giá gói, trạng thái công khai, timestamp và @Version; dates/stockPolicy dành cho phần sau |

## Quy tắc đang thực thi

Tạo Product BUNDLE ACTIVE và config DRAFT cùng các BundleItem. Không tạo SKU combo hoặc tồn kho combo. SKU phải hợp lệ; quantity nguyên 1..10000, 1..100 dòng không trùng; giá dương, tối đa DECIMAL(15,2), không vượt tổng lẻ. Unknown JSON/query và số lượng dạng chuỗi/thập phân bị từ chối.

Update giữ mã combo, từ chối PUBLISHED, thay toàn bộ danh sách; giữ ID item nếu SKU vẫn tồn tại. Khóa configuration PESSIMISTIC_WRITE, kiểm expectedVersion, touch timestamp và flush để @Version tăng kể cả chỉ sửa child/status. Stale trả 409. Tạo/sửa/lưu child thất bại rollback toàn bộ.

Status chỉ đổi Product ACTIVE/INACTIVE. DELETE đặt DELETED, giữ mã/ID/items; list/get thông thường ẩn bản ghi đã xóa. Sau khi lấy lock, update/status loại DELETED trước kiểm version nên trả 404 cả khi version cũ. DELETE vẫn kiểm version trước no-op tombstone: stale 409, version hiện tại 200 không tăng version. Không restore/hard delete.

Giá đọc theo sellingPrice SKU hiện tại. Mapper báo priceIssue khi dữ liệu giá không hợp lệ, không coi kết quả là cam kết mua/giữ tồn. Import guard chặn ghi đè combo từ luồng import cũ.

## Dấu vết và Audit

Product và BundleConfiguration giữ timestamp; config giữ version; Product.status lưu tombstone. Đây là dấu vết gần nhất, không phải lịch sử mọi lần sửa. Core đã ngừng gọi AuditApi vì chưa xác thực người thao tác. Module/bảng audit_entries còn tồn tại để tương thích migration, không tạo bản ghi CRUD mới.

createdBy/updatedBy/deletedBy sẽ lấy từ IAM khi tích hợp; không dùng User giả. Nếu cần lưu từng phiên bản giá/thành phần, có thể bổ sung bảng BundleRevision/BundleRevisionItem ghi cùng transaction sau khi chốt yêu cầu; hiện chưa triển khai.

## Môi trường và kiểm chứng

Java 21, Spring Boot 3.5.16, PostgreSQL; xem [migration runbook](migrations/SCRUM14-UC13-phase1.md). Migration cũ được giữ nguyên checksum/schema; không tự sửa file SQL đã phát hành.

Chạy `.\mvnw.cmd -Pphase1-it verify`; integration tests cần Docker/PostgreSQL Testcontainers. Bộ test HTTP không gửi token; fixture không dựng JWT/JWK server. Với môi trường Windows này, dùng Mockito javaagent và thêm module hệ thống vào PSModulePath theo [handoff hiện hành](../.agent/handoffs/SCRUM14-UC13-phase1-verification.md). Lần full verify sau sửa đạt 35 Surefire + 37 Failsafe = 72 test case, không lỗi/skipped, BUILD SUCCESS. Log: `uc13-iteration1-final-verify.log` tại Git root. Số 65 tests bản JWT cũ và 60 tests bản không Auth trước sửa chỉ là lịch sử.

Public/publish/media/tồn/options/Sales/preorder vẫn thuộc phần triển khai sau trong plan.

Iteration 1 chỉ nghiệm thu core B03-B06 Core DTO, B11/B12 và C01. Ma trận quyền, audit và DTO đầy đủ trong spec là thiết kế tích hợp tương lai; không thêm JWT verifier, audit writer hoặc bảng lịch sử mới để hoàn thành Phase 1. Schema audit có trong migration cũ được giữ nguyên, không tạo dependency runtime cho CRUD. Lần đồng bộ tài liệu 0.9.1 không chạy test; đợt code/test plan 0.9.2 đã chạy lại và đạt kết quả nêu trên. Core sẵn sàng review, chưa có Final Gate.
