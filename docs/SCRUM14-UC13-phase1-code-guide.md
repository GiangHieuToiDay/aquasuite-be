# Hướng dẫn code UC13 — Phase 1 và Phase 2: Quản lý combo

Đối chiếu spec 0.13.0 và code hiện tại, cập nhật ngày 2026-10-21; plan 0.11.0 là bằng chứng tích hợp authentication trước thay đổi response. Giữ tên file cũ để các liên kết đang có tiếp tục hoạt động. [Spec đầy đủ](specs/SCRUM14-UC13-product-combos.md), [plan và tiến độ](../.agent/plans/SCRUM14-UC13.md), [handoff Phase 1](../.agent/handoffs/SCRUM14-UC13-phase1-verification.md), [handoff Phase 2](../.agent/handoffs/SCRUM14-UC13-phase2-verification.md), [handoff authentication](../.agent/handoffs/authentication-merge-verification.md).

## Phạm vi và đường đi request

Phase 1 cung cấp danh sách/chi tiết/tạo/sửa/xóa mềm/bật tắt combo và tìm SKU. Phase 2 bổ sung lịch, publish/unpublish, option cùng giá và preview giá/tồn tại kho duy nhất. Request quản trị: HTTP/JWT → BundleAdminController → BundleAdminService → repository PostgreSQL → assembler/MapStruct → DTO → ApiResponse. Controller gọi cùng service cho `/bundles` và `/bundle-skus`; lookup giữ transaction read-only.

Mọi API combo hiện yêu cầu `Authorization: Bearer <access-token>` qua bộ lọc JWT dùng chung. Chỉ POST `/api/v1/auth/login` và `/api/v1/auth/refresh` không yêu cầu xác thực. Tên public/storefront không đồng nghĩa truy cập ẩn danh. User/actor dùng UUID; không dùng actor giả. Chưa bổ sung ma trận permission riêng cho Catalog.

## Các thành phần

| Thành phần | Trách nhiệm |
|---|---|
| BundleAdminController | HTTP quản lý combo và tìm SKU; validateQueryParameters chặn tham số lạ/lặp |
| BundleAdminService | Điều phối CRUD/lookup SKU, validation, transaction và version; dùng chung repository/mapper |
| BundlePricingService | BigDecimal, tổng giá lẻ, tiết kiệm và phần trăm giảm |
| BundleDetailAssembler | Tính giá/sellable từ dữ liệu đã truy vấn, gọi mapper tạo response |
| BundleCoreMapper | Interface MapStruct map response/item/SKU, không chứa nghiệp vụ hoặc gọi repository |
| BundleCoreCreateRequest/UpdateRequest | Định nghĩa combo đầu vào; update thêm expectedVersion |
| BundleCoreStatusRequest | ACTIVE/INACTIVE và expectedVersion |
| BundleResponse | Response đầy đủ dùng chung cho danh sách, GET, create, update, status, publish/unpublish |
| DELETE response | ApiResponse<Void>, HTTP 200 và message thành công, không có data |
| BundleItemRequest | variantId/quantity dùng chung cho create/update |
| BundleJsonDeserializers | StrictInteger/StrictLong/StrictMoney dùng chung |
| BundleConfiguration | Giá gói, lịch UTC, publication, timestamp, @Version và các nhóm option |
| BundleStorefrontController/Service | API xem combo đang hiển thị; giải quyết lựa chọn và preview giá/tồn |
| BundleComponentGroup/Request | SKU mặc định và whitelist các SKU cùng giá được phép thay thế |
| BundleVersionRequest | expectedVersion cho publish/unpublish |
| BundleEvaluationRequest/Evaluation | Số combo/lựa chọn đầu vào và ảnh chụp giá/tồn đầu ra |
| InventoryAvailabilityApi | Hợp đồng đọc tồn theo lô trong inventory/api, không giữ hàng |

## Quy tắc đang thực thi

Tạo Product BUNDLE ACTIVE và config DRAFT cùng các BundleItem. Không tạo SKU combo hoặc tồn kho combo. SKU phải hợp lệ; quantity nguyên 1..10000, 1..100 dòng không trùng; giá dương, tối đa DECIMAL(15,2), không vượt tổng lẻ. Unknown JSON/query và số lượng dạng chuỗi/thập phân bị từ chối.

Update giữ mã combo, từ chối PUBLISHED, thay toàn bộ danh sách; giữ ID item nếu SKU vẫn tồn tại. Khóa configuration PESSIMISTIC_WRITE, kiểm expectedVersion, touch timestamp và flush để @Version tăng kể cả chỉ sửa child/status. Stale trả 409. Tạo/sửa/lưu child thất bại rollback toàn bộ.

Status chỉ đổi Product ACTIVE/INACTIVE. DELETE đặt DELETED, giữ mã/ID/items; list/get thông thường ẩn bản ghi đã xóa. Sau khi lấy lock, update/status loại DELETED trước kiểm version nên trả 404 cả khi version cũ. DELETE vẫn kiểm version trước no-op tombstone: stale 409, version hiện tại 200 không tăng version. Không restore/hard delete.

Giá đọc theo sellingPrice SKU hiện tại. PricingService trả priceIssue khi giá không hợp lệ; assembler chuyển kết quả qua MapStruct, không coi là cam kết mua/giữ tồn. Import guard chặn ghi đè combo từ luồng import cũ.

## Query validation và các DTO có field chung

Spring bind các query đã khai báo nhưng có thể bỏ qua tên query lạ hoặc lấy một giá trị khi query bị lặp. validateQueryParameters giữ contract 400 SYS_002 cho `?surprise=x` hoặc `?page=0&page=1`; mỗi endpoint khai báo danh sách tên được phép. Helper này là validation đầu vào, không phải Auth. Khi bỏ helper, phải thay contract và test tương ứng.

Create/update có các field definition, items, packagePrice, validFrom/validUntil và componentGroups; tách DTO vì create không nhận expectedVersion còn update bắt buộc có. List/get/create/update/status/publish/unpublish dùng chung BundleResponse đầy đủ, gồm items/mô tả/lịch/nhóm. Không còn summary/mutation DTO hoặc bước map detail sang summary. Request dùng chung BundleItemRequest, BundleComponentGroupRequest và các bộ đọc số strict.

DELETE trả HTTP 200 ApiResponse<Void> với message “Đã xóa combo.”, không có data/version. Client bỏ dòng sau thành công; retry với version trước khi xóa vẫn trả 409 theo rule hiện tại. Status trả BundleResponse đầy đủ cùng version mới; no-op giữ nguyên version. JSON danh sách lớn hơn do trả cả cấu hình chi tiết.

MapStruct dùng componentModel Spring và unmappedTargetPolicy ERROR để build lỗi khi thiếu field target mới. Processor dùng chung compile/testCompile, thêm Lombok binding theo [hướng dẫn MapStruct](https://mapstruct.org/documentation/1.6/reference/html/#_lombok). Generated implementation nằm trong target, không đưa lên Git.

## Vì sao chỉ gộp một phần service?

BundleAdminService và SKU lookup cùng phục vụ màn hình quản lý, cùng repository/mapper nên đã gộp. Controller không cần điều phối hai bean nữa. BundlePricingService được gọi từ validation ghi và assembler đọc; giữ công thức giá ở đây giúp hai luồng dùng chung và kiểm thử không cần DB. BundleDetailAssembler chỉ là helper nhận dữ liệu đã đọc để ghép response, không sở hữu use case hoặc transaction riêng. Ở Phase 1 chỉ giữ một application service và các helper có nhiệm vụ cụ thể.

JavaDoc trước controller/service/DTO mô tả mục đích, caller và luồng xử lý. Các bước khóa/version, xóa mềm/no-op, lọc SKU trước phân trang, preview so với validate giá và strict JSON được chú thích tại method tương ứng.

## Dấu vết và Audit

Product và BundleConfiguration giữ timestamp; config giữ version; Product.status lưu tombstone. Đây là dấu vết gần nhất, không phải lịch sử mọi lần sửa. Theo yêu cầu người dùng ngày 2026-10-21 (spec 0.13.0), module Java Audit chưa được sử dụng đã được bỏ. Bảng audit_entries và migration V2/V4 vẫn giữ nguyên để bảo toàn tương thích/dữ liệu lịch sử; CRUD combo không ghi audit.

createdBy/updatedBy/deletedBy chưa được tích hợp vào luồng CRUD combo; khi bổ sung phải lấy từ IAM, không dùng User giả. Bảng lưu từng phiên bản giá/thành phần chưa triển khai.

## Môi trường và kiểm chứng

Java 21, Spring Boot 3.5.16, PostgreSQL; xem [migration runbook](migrations/flyway.md). Flyway quản lý toàn bộ schema; không sửa migration đã áp dụng lên database dùng chung.

Chạy `./mvnw.cmd -Pphase1-it verify` (thêm `clean` nếu cần xóa output build cũ). Tên profile lịch sử vẫn bao gồm cả Phase 2 và tích hợp hiện tại. Integration tests cần Docker/PostgreSQL/Redis Testcontainers. Test HTTP core cô lập filter để kiểm nghiệp vụ; AuthenticationMergeIT kiểm JWT thật với full application. Cấu hình Mockito javaagent/Windows xem handoff tương ứng. Secret JWT/database/Redis lấy từ môi trường.

Kết quả lịch sử gần nhất sau tích hợp authentication: verify PASS ngày 2026-10-20 17:56:28 +07, 186 Surefire + 65 Failsafe = 251 test, không failures/errors/skipped. Surefire có cả test database/Redis, không phải 186 unit test thuần. Bằng chứng ở handoff authentication và log auth-merge-final-verify.log/XML trong target. Các số 85 (core) và 111 (Phase 2 trước auth) là lịch sử. Đợt cập nhật tài liệu này không chạy lại bộ test.

Media, ghi business Audit, Sales/giỏ hàng/đặt hàng/giữ tồn và preorder vẫn thuộc phần sau. Kết quả kiểm thử không thay thế Code Review/Final Gate hoặc chứng minh đã nghiệm thu toàn UC-13.

## Database migrations

Flyway là cơ chế migration duy nhất: V1 tạo schema legacy, V2 bổ sung/backfill combo, V3 thêm bundle_component_groups, V4 tích hợp IAM UUID và V5 bảo toàn dữ liệu recovery. Integration tests chạy migration rồi Hibernate validate. V1/V2/V3 giữ nguyên khi ghép auth; không sửa migration đã áp dụng. Database hiện hữu cần onboarding theo [runbook](migrations/flyway.md), không tự baseline/repair khi khác lịch sử.

## Phase 2: API và luồng dùng lại

Code Catalog nằm tại `src/main/java/com/aquasuite/ims/module/catalog/internal/`. `BundleStorefrontService` tách khỏi service quản trị vì có điều kiện hiển thị/lựa chọn/preview riêng, nhưng dùng lại validatePageRequest/loadDetails và pricing. Mapper được gọi qua luồng loadDetails dùng chung. Luồng: JWT → BundleStorefrontController → BundleStorefrontService → Catalog; evaluation gọi thêm `module/inventory/api/InventoryAvailabilityApi`. Catalog không import internal của Inventory.

Các endpoint bổ sung dưới `/api/v1/catalog` đều yêu cầu JWT:

| Method | Đường dẫn | Đầu vào / kết quả |
|---|---|---|
| POST | `/admin/bundles/{id}/publish` | JSON expectedVersion → detail |
| POST | `/admin/bundles/{id}/unpublish` | JSON expectedVersion → detail |
| GET | `/bundles` | page, size, keyword → trang BundleResponse đầy đủ |
| GET | `/bundles/{id}` | Detail đang hiển thị; bị ẩn trả 404 |
| POST | `/bundles/{id}/evaluations` | quantity, expectedVersion tùy chọn, selections → preview |

Publish/unpublish gửi `{"expectedVersion": 0}` với version mới nhất vừa đọc, không cố định dùng 0. Query lạ/lặp bị từ chối như core. Danh sách mặc định page=0, size=20.

## Phase 2: lịch và trạng thái công bố

Nháp được thiếu cả validFrom/validUntil; nếu có lịch phải đủ hai mốc và bắt đầu trước kết thúc. Dùng Instant/UTC. Product ACTIVE/INACTIVE độc lập với publication DRAFT/PUBLISHED/UNPUBLISHED.

Publish khóa cấu hình, kiểm version, yêu cầu Product ACTIVE, kiểm lại definition/SKU/giá và toàn bộ whitelist. Phải có lịch, validUntil còn ở tương lai. Cho phép publish trước validFrom để lên lịch. Combo ACTIVE đã PUBLISHED với version hiện tại trả detail không ghi/kiểm lại giá và lịch; stale vẫn bị từ chối trước.

Unpublish đổi PUBLISHED → UNPUBLISHED, tăng version; vẫn dùng được với combo inactive/hết hạn. DRAFT bị từ chối. Retry UNPUBLISHED với version hiện tại là no-op. Cần unpublish trước khi sửa definition PUBLISHED.

Storefront chỉ hiển thị Product BUNDLE ACTIVE, cấu hình PUBLISHED, thời điểm hiện tại thuộc `[validFrom, validUntil)`: gồm mốc bắt đầu, không gồm kết thúc. Combo bị ẩn/hết hạn trả 404 ở get/evaluate. Không có job tự đổi publication khi hết hạn; truy vấn quyết định visibility. Lọc trước phân trang, sort createdAt giảm rồi productId tăng, đọc detail theo lô. Mỗi lượt xử lý lấy một mốc now để kiểm nhất quán.

PUT thay toàn bộ lịch và componentGroups: bỏ dates sẽ xóa lịch; componentGroups thiếu/null thành rỗng. Client cần gửi lại dữ liệu muốn giữ. Constructor tương thích cách gọi core cũ cũng đặt dates null và nhóm rỗng.

## Phase 2: nhóm option cùng giá

Mỗi BundleComponentGroup thay một defaultVariantId đang có trong items. groupId không trùng; mỗi default chỉ thuộc một nhóm. allowedVariantIds có 1..100 UUID không trùng, chứa chính default. Tất cả SKU phải ACTIVE, thuộc Product NORMAL ACTIVE, có sellingPrice bằng default theo BigDecimal.compareTo.

Kiểm toàn bộ whitelist khi create/update và kiểm lại khi publish. Evaluation kiểm lại SKU được chọn vì giá/trạng thái có thể đổi sau publish. Không phụ thu hoặc đổi packagePrice theo option. Thay SKU giữ quantity của item gốc; nhóm không được chọn giữ default. Từ chối nhóm lạ/lặp, lựa chọn ngoài whitelist hoặc làm hai item cuối trùng SKU.

Nhóm thuộc cấu hình bằng ElementCollection, lưu bundle_component_groups; whitelist là PostgreSQL uuid[], BatchSize(100) tránh tải từng nhóm cho từng combo. Không tạo repository, SKU hoặc tồn riêng cho option.

Ví dụ các field bổ sung vào create/update (UUID minh họa, cần thay bằng ID thật và gửi đủ field core):

```json
{
  "validFrom": "2026-11-01T00:00:00Z",
  "validUntil": "2026-12-01T00:00:00Z",
  "componentGroups": [{
    "groupId": "11111111-1111-4111-8111-111111111111",
    "defaultVariantId": "22222222-2222-4222-8222-222222222222",
    "allowedVariantIds": [
      "22222222-2222-4222-8222-222222222222",
      "33333333-3333-4333-8333-333333333333"
    ]
  }]
}
```

## Phase 2: evaluation giá và tồn

Ví dụ body gửi POST evaluations:

```json
{
  "quantity": 2,
  "expectedVersion": 3,
  "selections": [{
    "groupId": "11111111-1111-4111-8111-111111111111",
    "variantId": "33333333-3333-4333-8333-333333333333"
  }]
}
```

quantity nguyên 1..10000; selections thiếu/null thành rỗng. expectedVersion tùy chọn, nếu gửi phải khớp; version cấu hình không đại diện cho biến động giá SKU hoặc tồn kho.

Luồng evaluate: kiểm visibility/version → ghép lựa chọn → đọc SKU/parent theo lô → kiểm option cùng giá → dùng BundlePricingService → nhân quantity → đọc tồn theo lô → tạo BundleEvaluation. Catalog đọc bằng transaction REPEATABLE_READ; không giữ hàng hoặc tạo giỏ hàng.

| Kết quả | Ý nghĩa |
|---|---|
| packagePrice | Giá một combo |
| totalPackagePrice | Giá một combo × quantity |
| originalTotal, savingAmount | Tổng lẻ/tiết kiệm cho quantity combo; tiết kiệm null khi có issue |
| discountPercent | Tỷ lệ giảm, không nhân quantity; null khi có issue |
| items.requiredQty | Số lượng item gốc × quantity |
| items.availableQty | Tồn khả dụng tại kho duy nhất; null là chưa biết |
| IN_STOCK | Có dữ liệu và đủ tồn mọi SKU |
| INSUFFICIENT | Biết tồn mọi SKU và có SKU thiếu |
| UNKNOWN | Ít nhất một SKU thiếu dữ liệu hoặc đọc tồn lỗi; ưu tiên hơn INSUFFICIENT |
| missingVariantIds | Chỉ SKU có dữ liệu và xác định thiếu, không gồm SKU chưa biết |
| purchasable | Không có priceIssue và IN_STOCK tại lúc preview |

Giá lấy sellingPrice hiện tại; thành phần không còn bán được có thể trả COMPONENT_UNAVAILABLE. Tổng tiền sau nhân vượt DECIMAL(15,2) bị từ chối. Preview chỉ là ảnh chụp dữ liệu, không bảo đảm cho giao dịch mua sau.

InventoryAvailabilityApi tái sử dụng bộ đọc theo lô: khả dụng = onHand - reserved tại một kho. SKU không có bản ghi bị bỏ khỏi map, không coi là 0. InventoryServiceImpl dùng read-only REQUIRES_NEW để lỗi truy vấn Inventory không làm Catalog rollback-only. Khi phụ thuộc lỗi, Catalog trả UNKNOWN và log ID combo/loại lỗi; lỗi Catalog vẫn được báo bình thường. Hợp đồng đọc không gọi IAM riêng, không ghi/giữ tồn; HTTP vẫn qua JWT.

## Lỗi Phase 2 và nơi đọc test

| Mã | HTTP | Ý nghĩa |
|---|---|---|
| CATALOG_BUNDLE_001 | 400 | Thành phần/nhóm/lựa chọn không hợp lệ |
| CATALOG_BUNDLE_002 | 400 | Definition/giá/lịch/tổng tiền không hợp lệ |
| CATALOG_BUNDLE_003 | 409 | Version cũ |
| CATALOG_BUNDLE_006 | 409 | Thao tác không hợp lệ với trạng thái |

Dùng ApiResponse và handler chung cho validation, 404 và authentication, không tạo envelope riêng. Comment tiếng Việt trong controller/service/DTO giải thích luồng, khóa/version, option, thời hạn và giới hạn preview.

Đọc BundleStorefrontServiceTest và BundlePhase2IT cho lịch/options/evaluation; BundleConcurrencyIT/BundleStatusIT/BundleDeletionIT cho khóa, retry và xóa mềm; BundleCoreBoundaryTest cho ranh giới; AuthenticationMergeIT/AuthenticationUpgradeIT cho JWT thực và bảo toàn dữ liệu IAM. Các test nằm dưới `src/test/java/com/aquasuite/ims/`.
