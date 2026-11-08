# UC-13 — View and Manage Product Combos

## Metadata

- **Work ID**: `UC13-SPEC`
- **Feature / Use Case**: `FE-03 / UC-13` — Xem và quản lý combo sản phẩm
- **Version**: `1.1.0`
- **Owner**: Product Owner / Catalog owner
- **Owning Module**: `catalog`
- **Risk Reasons**: `money`, `inventory`, `concurrency`, `security`, `public contract`, `cross-module`, `data migration`
- **Design Review**: `PENDING`
- **Decision Owner**: Product Owner / Tech Lead / Catalog, IAM, Inventory và Sales owners
- **Design references**: `D:/class_combo.drawio`, `D:/sequnce_combo.drawio` (file local, không phải dependency build hoặc nguồn tài liệu được Git quản lý).

Đặc tả này thay thế tài liệu UC-13 mang tên SCRUM14 và code guide cũ. Những thay đổi đã được người dùng chốt: search thuộc QueryService; entity dùng getter/setter; logic/validation thuộc service; DTO `CreateBundleRequest`; PATCH gộp publication; truy vấn tên theo batch; chỉ SKU cố định, bỏ hoàn toàn SKU thay thế. Ngày 2026-10-27, người dùng yêu cầu triển khai spec và giới hạn chỉ thay đổi class bundle. Đây là ủy quyền triển khai trực tiếp, không phải bằng chứng independent Design Review hoặc chứng nhận nghiệm thu toàn bộ hệ thống.

## 1. Goal and Scope

### Problem and Desired Outcome

Owner cấu hình một bộ SKU với số lượng và giá gói. Client xem danh sách/chi tiết, phần tiết kiệm và khả năng đáp ứng theo tồn khả dụng. Mọi kênh dùng một định nghĩa combo nhất quán; thay đổi Catalog không sửa dữ liệu giao dịch đã chấp nhận trong Sales.

### In Scope

- Bảy chức năng: xem danh sách, xem chi tiết, tạo, sửa, tìm kiếm, publish/unpublish và xóa mềm.
- SKU lookup phục vụ chọn thành phần; tính giá và kiểm tra tồn theo số bộ yêu cầu.
- Trạng thái sản phẩm ACTIVE/INACTIVE/DELETED độc lập với publication DRAFT/PUBLISHED/UNPUBLISHED.
- Thời hạn UTC, optimistic concurrency, strict validation, response envelope và phân trang.
- Thiết kế controller/service/repository/mapper, HTTP payload và contract Inventory.

### Out of Scope

- SKU thay thế, nhóm lựa chọn, phụ phí lựa chọn và tự động đổi SKU.
- Preorder, giữ/nhả/trừ tồn, checkout/POS, tạo giỏ hàng/báo giá/đơn hàng hoặc thanh toán.
- Upload/banner/Cloudinary và audit nghiệp vụ combo trong lần thiết kế này.
- Thay đổi frontend ở repository khác, JWT/session model hoặc deploy/migrate database thật.

Đây là phạm vi UC-13 thu gọn theo quyết định mới; không tuyên bố đáp ứng các yêu cầu thay thế SKU/preorder/tích hợp Sales trong phạm vi lịch sử rộng hơn.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role/Permission | Data Restrictions |
|---|---|---|---|
| Store Owner | List/detail quản trị, lookup SKU, create/update/status/publication/delete | Access JWT hợp lệ; authority `ROLE_SHOP_OWNER` hiện hành do IAM cấp | Không nhận actor hoặc role do client tự khai |
| User đã xác thực, ACTIVE | Browse/detail/evaluate combo hiệu lực | Access JWT hợp lệ, session còn hiệu lực | Không đọc draft/inactive/deleted qua storefront |
| Anonymous / Guest | Chưa cho phép trong baseline này | Không có access JWT -> 401 | Không mở allowlist mới |

- Tái sử dụng security chain hiện có: JWT, Redis session, tài khoản và authorities PostgreSQL; không dựng identity fallback.
- Owner-only management được enforce tại BundleAdminController bằng `@PreAuthorize("hasRole('SHOP_OWNER')")`. Dùng role chuẩn đã có; không sửa IAM, security chain hoặc cấu hình quyền của module khác.
- Không trả costPrice, supplier, token, password hash hoặc credential. UUID tham chiếu không cấp quyền truy cập.
- Không log Authorization, session key, credential hoặc dump request chứa dữ liệu nhạy cảm.

## 3. Business Rules

1. Combo gắn với Product loại BUNDLE; tên/mã/mô tả/category/brand thuộc Product. Không tạo SKU hoặc tồn kho riêng cho combo.
2. Một bộ gồm SKU cố định và số lượng nguyên 1..10000; tối đa 100 dòng. Reject SKU trùng, SKU không tồn tại, SKU/product cha không ACTIVE, SKU thuộc BUNDLE và nesting. Không tự sửa số lượng sai thành 1.
3. Create/update yêu cầu ít nhất một item trong contract mục tiêu này. Draft được thiếu lịch, nhưng không cho draft rỗng. Đây là lựa chọn giữ validation baseline, thay cho đề xuất draft rỗng trong báo cáo ban đầu.
4. `originalTotal = sum(currentSellingPrice * item.quantity)`. Giá gói do Owner nhập, phải dương và không lớn hơn originalTotal. Không tin giá hoặc tổng do client tự tính.
5. `savingAmount = originalTotal - packagePrice`; `discountPercent = savingAmount / originalTotal * 100`. BigDecimal; tiền scale 2, phần trăm scale 1, HALF_UP. Input tiền tối đa 13 chữ số nguyên, 2 chữ số lẻ; không ngầm làm tròn input sai precision. Tổng một bộ và tổng theo requestedQuantity không vượt 9999999999999.99.
6. Giá lẻ dùng giá hiện tại khi đọc/ghi/publish/evaluate. Giá snapshot giao dịch thuộc Sales; `originalTotal` không phải cam kết khóa giá. Không lưu originalPrice/priceSnapshot trên definition chỉ để thay snapshot Sales.
7. Create sinh Product ACTIVE và configuration DRAFT, version do server cấp. Product code bất biến và không tái sử dụng sau xóa mềm.
8. Lịch cùng null hoặc cùng có giá trị; nếu có thì validFrom < validUntil. Publish bắt buộc có lịch, validUntil > now; được publish trước validFrom. Hiệu lực là `[validFrom, validUntil)` UTC; Timestamp được Jackson JavaTime của dự án đọc thành Instant; không có deserializer riêng của Bundle.
9. Storefront chỉ hiển thị Product ACTIVE, publication PUBLISHED và đang trong thời hạn. ID bị ẩn/missing/deleted trả 404. Admin có thể xem draft/inactive nhưng không xem deleted qua API thông thường.
10. Phải unpublish trước khi sửa nội dung. Publish kiểm lại SKU, giá, lịch và trạng thái Product. Unpublish không yêu cầu còn hạn hoặc Product ACTIVE; DRAFT -> UNPUBLISHED bị từ chối.
11. Đổi ACTIVE/INACTIVE không tự publish/unpublish và không đổi tồn. Cùng trạng thái với version hiện tại là no-op.
12. Update/publication/status/delete bắt buộc expectedVersion; kiểm stale trước no-op. PUT hợp lệ tăng version kể cả cùng nội dung. No-op publication/status/delete không tăng version.
13. Xóa mềm Product.status=DELETED, giữ configuration/items/mã và lịch sử giao dịch. Không hard delete hoặc cascade xóa item. Tham chiếu Sales không tự chặn soft delete; giao dịch cũ tiếp tục dùng snapshot của Sales.
14. List/search dùng cùng endpoint với keyword và bộ lọc; lọc trước phân trang, sort ổn định createdAt DESC rồi ID ASC. Không có kết quả trả 200 và trang rỗng. Lấy tên/thông tin SKU theo batch, không gọi API trong vòng lặp từng combo.
15. Evaluation chỉ đọc: requiredQty = item.quantity * requestedQuantity; availableQty lấy Inventory. Chỉ có hai trạng thái AVAILABLE và UNAVAILABLE. Tất cả SKU đọc được và đủ số lượng mới AVAILABLE; thiếu hàng, thiếu dòng tồn hoặc lỗi Inventory đều UNAVAILABLE, purchasable=false. availableQty giữ null nếu không đọc được, không tự đổi thành0; missingVariantIds chỉ chứa SKU xác định thiếu.
16. Evaluation có SKU inactive/giá không còn hợp lệ trả preview với priceIssue và purchasable=false; không tự sửa packagePrice. Chỉ đủ dữ liệu, đủ tồn và giá/definition hợp lệ mới purchasable=true. Preview không bảo đảm tồn cho giao dịch tiếp theo.

| From | Action | To | Conditions/Invariants |
|---|---|---|---|
| Chưa có | Create | ACTIVE / DRAFT | Definition/giá hợp lệ, lịch có thể null |
| DRAFT / UNPUBLISHED | Publish | PUBLISHED | ACTIVE, có item, SKU/giá/lịch hợp lệ, version khớp |
| PUBLISHED | Unpublish | UNPUBLISHED | Version khớp |
| PUBLISHED / UNPUBLISHED | Yêu cầu cùng publication | Không đổi | Kiểm stale trước no-op |
| DRAFT | Unpublish | Từ chối 409 | Không có transition này |
| DRAFT / UNPUBLISHED | Update | Giữ publication | Thay toàn bộ nội dung được sửa; mã bất biến |
| PUBLISHED | Update | Từ chối 409 | Unpublish trước |
| ACTIVE / INACTIVE | Change product status | ACTIVE / INACTIVE | Publication và tồn không đổi |
| Chưa xóa | Delete | DELETED | Version khớp, giữ definition |
| DELETED | Delete retry | Không đổi | Current version no-op; stale version 409 |

## 4. Data and Migration Decisions

### Owned Data

| Type/table | Fields / ownership |
|---|---|
| Product / products | productId UUID, productCode, productName, description, categoryId, brandId, productType=BUNDLE, status, timestamps |
| BundleConfiguration / bundle_configurations | bundleProductId UUID là PK/FK Product, packagePrice DECIMAL(15,2), validFrom/validUntil Instant, publicationStatus, stockPolicy, @Version Long, timestamps |
| BundleItem / bundle_items | bundleItemId UUID, bundleProductId UUID, variantId UUID, quantity Integer, createdAt Instant |

- Giữ identity/schema baseline: không thêm bundleConfigurationId độc lập, originalPrice hoặc item.priceSnapshot. Đây là điều chỉnh có chủ đích so với class diagram đề xuất cũ; cần đồng bộ UML khi chốt review.
- Entity chỉ giữ trạng thái/getter/setter; service quản lý validation, collection và lifecycle.
- Aggregate logic gồm Product + Configuration + Items; repository adapter lưu toàn bộ trong cùng transaction. Một repository interface không đồng nghĩa bắt buộc một bảng hoặc JPA cascade.
- Unique `(bundle_product_id, variant_id)`, quantity > 0, unique productCode kể cả tombstone, FK hợp lệ và version bắt buộc được bảo vệ ở database phù hợp schema hiện hành.
- stockPolicy cố định `CHECK_COMPONENT_STOCK`; client không được chọn chế độ giữ hàng/preorder.
- Chỉ tham chiếu Inventory/IAM/Sales bằng scalar ID hoặc public DTO; không JPA association chéo module.

### Migration and Compatibility

- Xóa class BundleComponentGroup và mapping componentGroups khỏi BundleConfiguration theo yêu cầu dọn SKU thay thế. Bảng `bundle_component_groups` và dữ liệu cũ giữ nguyên trong database, không còn được JPA Bundle quản lý. Definition chỉ dùng SKU trong bundle_items. Không drop/backfill hoặc rewrite dữ liệu; cần kiểm kê tác động client trước rollout.
- Không đổi V1 baseline đã có. Nếu cần constraint/schema/backfill, tạo forward migration được review; không sửa lịch sử Flyway hoặc tự repair/baseline.
- Không yêu cầu cascade/orphanRemoval như một quyết định bắt buộc; cơ chế lưu children là implementation detail nếu giữ atomicity và integrity.
- Không di chuyển hoặc sửa snapshot Sales. Recovery từ rollout lỗi phải bảo toàn dữ liệu; không rollback bằng xóa definition hay database.

## 5. Contracts

### HTTP API

Base prefix `/api/v1/catalog/bundle`. Mọi JSON response bọc `ApiResponse<T>`; bảng dưới mô tả `data`. Đây là contract mục tiêu, không phải danh sách route đã có đầy đủ.

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| GET /admin | Owner | BundlePageRequest | 200, PageResponse<BundleResponse> | 400, 401, 403, 503 |
| GET /admin/{id} | Owner | UUID id; không body/query | 200, BundleResponse | 400, 401, 403, 404, 503 |
| POST /admin | Owner | CreateBundleRequest | 201, BundleResponse; Location trỏ admin detail | 400, 401, 403, 409, 503 |
| PUT /admin/{id} | Owner | UpdateBundleRequest; thay toàn bộ definition được sửa | 200, BundleResponse | 400, 401, 403, 404, 409, 503 |
| PATCH /admin/{id}/publication-status | Owner | ChangePublicationStatusRequest | 200, BundleResponse | 400, 401, 403, 404, 409, 503 |
| PATCH /admin/{id}/status | Owner | ChangeBundleStatusRequest | 200, BundleResponse | 400, 401, 403, 404, 409, 503 |
| DELETE /admin/{id}?expectedVersion={version} | Owner | Required nonnegative long | 200, ApiResponse<Void>, message thành công, không data | 400, 401, 403, 404, 409, 503 |
| GET /admin/skus | Owner | page/size/keyword | 200, PageResponse<BundleSkuResponse> | 400, 401, 403, 503 |
| GET / | Authenticated ACTIVE user | page/size/keyword | 200, PageResponse<BundleResponse>, chỉ hiệu lực | 400, 401, 403, 503 |
| GET /{id} | Authenticated ACTIVE user | UUID id; không body/query | 200, BundleResponse | 400, 401, 403, 404, 503 |
| POST /{id}/evaluations | Authenticated ACTIVE user | BundleEvaluationRequest; chỉ đọc | 200, BundleEvaluationResponse | 400, 401, 403, 404, 409, 503 |

Search dùng GET /admin?keyword=...; không thêm route search riêng. Query lạ/lặp bị từ chối 400; reject unknown JSON field. ID malformed, enum/number sai trả 400. Không dùng 422 trong contract này.

### Request DTOs

| DTO | Fields / validation |
|---|---|
| BundlePageRequest | Kế thừa PageQuery dùng chung; keyword optional <=255; publicationStatus optional enum; productStatus optional ACTIVE/INACTIVE (admin); page integer >=0 default 0; size 1..100 default 20 |
| CreateBundleRequest | productCode required trimmed 1..100; name required trimmed 1..255; categoryId required UUID; brandId optional UUID; description optional <=10000; items required 1..100; packagePrice required; validFrom/validUntil optional pair |
| UpdateBundleRequest | Các field create, cùng productCode bất biến; expectedVersion required nonnegative long; PUT thay toàn bộ items/description/lịch, omitted optional fields được clear |
| BundleItemRequest | variantId required UUID; quantity required integer 1..10000; không nhận giá hoặc substitutes |
| ChangePublicationStatusRequest | publicationStatus required PUBLISHED hoặc UNPUBLISHED; expectedVersion required nonnegative long; không nhận DRAFT |
| ChangeBundleStatusRequest | productStatus required ACTIVE hoặc INACTIVE; expectedVersion required nonnegative long |
| BundleEvaluationRequest | quantity required integer 1..10000; expectedVersion optional nonnegative long; không nhận selections hoặc giá |

Category/brand phải tồn tại và hợp lệ; request không nhận productType, stockPolicy, publication mặc định, calculated totals, actor hoặc timestamps. JSON được bind bằng Jackson mặc định như các module khác, bao gồm quy tắc coercion số/chuỗi/enum của cấu hình chung. Bean Validation kiểm tra required, miền giá trị và độ chính xác sau binding; không thêm cấu hình Jackson toàn cục hoặc deserializer riêng cho Bundle.

Ví dụ tạo combo:

```json
{
  "productCode": "COMBO-BATH-01",
  "name": "Combo phòng tắm",
  "categoryId": "11111111-1111-4111-8111-111111111111",
  "description": "Bộ SKU cố định",
  "items": [
    {"variantId": "22222222-2222-4222-8222-222222222222", "quantity": 1},
    {"variantId": "33333333-3333-4333-8333-333333333333", "quantity": 2}
  ],
  "packagePrice": 7200000.00,
  "validFrom": "2026-11-01T00:00:00Z",
  "validUntil": "2026-12-01T00:00:00Z"
}
```

Publication body: `{"publicationStatus":"PUBLISHED","expectedVersion":0}`. Evaluation body: `{"quantity":2,"expectedVersion":1}`.

### Response DTOs

| DTO | Fields / semantics |
|---|---|
| PageResponse<T> | items array, page, size, totalElements, totalPages; empty items=[]; page vượt cuối trả trang rỗng |
| BundleResponse | bundleId (=Product ID), productCode, name, productStatus, publicationStatus, version, linkedItemsCount, originalTotal, packagePrice, savingAmount, discountPercent, priceIssue, createdAt, updatedAt, categoryId, brandId, description, items, validFrom, validUntil |
| BundleItemResponse | bundleItemId, variantId, sku, name, quantity, sellingPrice hiện tại, sellable boolean |
| BundleSkuResponse | variantId, productId, sku, variantName, productName, sellingPrice; không costPrice/tồn; chỉ NORMAL ACTIVE SKU thuộc Product ACTIVE |
| BundleEvaluationResponse | bundleId, bundleVersion, evaluatedAt, quantity, packagePrice (mỗi bộ), totalPackagePrice, originalTotal, savingAmount, discountPercent, priceIssue, availability, purchasable, missingVariantIds, items |
| BundleItemAvailabilityResponse | variantId, sku, name, requiredQty, availableQty nullable khi chưa biết |

linkedItemsCount là số dòng SKU phân biệt, không phải tổng quantity. Response không có componentGroups/substitutes. List/detail/create/update/status/publication dùng cùng BundleResponse đầy đủ. priceIssue nullable: INVALID_RETAIL_TOTAL, PACKAGE_PRICE_REQUIRED hoặc PACKAGE_EXCEEDS_RETAIL; có lỗi giá thì savingAmount/discountPercent không khả dụng (null hoặc omitted theo Jackson), không giả bằng 0. Item inactive làm purchasable=false dù công thức giá vẫn tính được.

Evaluation: availability = AVAILABLE / UNAVAILABLE; missingVariantIds chỉ gồm SKU có số tồn đã biết và thấp hơn nhu cầu. Tổng tiền áp dụng số bộ yêu cầu, riêng packagePrice là giá một bộ; discountPercent không đổi. Danh sách items luôn có dữ liệu để chỉ rõ shortage. Admin draft vẫn đọc được để sửa giá; storefront ID không hiệu lực luôn404.

### Payload Packages

- `internal/payload/req`: CreateBundleRequest, UpdateBundleRequest, BundleItemRequest, BundlePageRequest, ChangeBundleStatusRequest, ChangePublicationStatusRequest, BundleEvaluationRequest. Không có helper deserializer riêng trong package request.
- `internal/payload/res`: BundleResponse, BundleEvaluationResponse, BundleSkuResponse. PageResponse<T> thuộc share/dto, không tạo DTO phân trang riêng trong bundle.
- Xóa BundleCoreCreateRequest, BundleVersionRequest và bridge Java cũ; publication chỉ nhận ChangePublicationStatusRequest. Không giữ alias response ở req. Không sửa caller/test ngoài bundle để bù việc đổi package.

### Error Contract

| HTTP / code | Condition |
|---|---|
| 400 SYS_002 / SYS_003 | Query/type/Bean Validation sai theo handler chung |
| 400 CATALOG_BUNDLE_001 | Thành phần SKU/quantity/nesting/duplicate không hợp lệ |
| 400 CATALOG_BUNDLE_002 | Giá, lịch hoặc definition không hợp lệ; evaluation overflow |
| 404 SYS_004 | Missing/deleted; storefront không hiệu lực |
| 409 CATALOG_BUNDLE_003 | expectedVersion stale hoặc optimistic conflict |
| 409 CATALOG_BUNDLE_006 | Update PUBLISHED, publish INACTIVE, unpublish DRAFT hoặc transition không hợp lệ |
| 409 DB_001 | Trùng productCode/constraint integrity, kể cả cạnh tranh create |
| 401 AUTH_001 / AUTH_007 | Access token không hợp lệ hoặc session revoked theo IAM |
| 403 AUTH_002 / AUTH_008 | Thiếu quyền quản trị hoặc tài khoản không ACTIVE theo IAM |
| 503 AUTH_009 | Authentication infrastructure không khả dụng theo IAM |
| 500 SYS_001 | Lỗi không phân loại; không expose SQL/stack trace |

Inventory failure sau authentication trong evaluation trả 200 UNAVAILABLE với purchasable=false, không đổi thành lỗi auth. PostgreSQL lỗi thuộc Catalog không được tự gán AUTH_009; dùng handler chuẩn nếu chưa có mã hạ tầng Catalog được duyệt.

### Components and Methods

| Component | Responsibility / methods |
|---|---|
| BundleAdminController | Inject BundleAdminService và BundleQueryService interfaces; bind/validate/bọc ApiResponse; không repository hoặc pricing |
| BundleController | Inject BundleQueryService và BundleEvaluationService; browse/detail/evaluate; không expose admin draft |
| BundleAdminService / BundleAdminServiceImpl | createBundle(CreateBundleRequest), updateBundle(UUID, UpdateBundleRequest), changePublicationStatus(UUID, ChangePublicationStatusRequest), changeProductStatus(UUID, ChangeBundleStatusRequest), deleteBundle(UUID, long expectedVersion) |
| BundleQueryService / BundleQueryServiceImpl | getBundleList(BundlePageRequest), getBundleDetails(UUID) cho admin; searchBundles(BundlePageRequest) dùng chung pipeline list; getPublishedBundles(BundlePageRequest), getPublishedBundleDetails(UUID); searchSkus(page,size,keyword) |
| BundleEvaluationService | calculateOriginalPrice(lines), validatePackagePrice(packagePrice, originalTotal), calculateDiscountAmount(...), calculateDiscountPercentage(...), checkAvailability(UUID, BundleEvaluationRequest); logic/validation và orchestration đọc Inventory |
| BundleRepository | Adapter tải Product/config/items/SKU/parent theo batch: findBundleProduct(UUID, includeDeleted), loadAggregates(List<Product>); trả BundleData chứa dữ liệu persistence. Không tính giá, gọi mapper hoặc ghép HTTP response. AdminServiceImpl dùng private Spring Data repositories hiện có để ghi cùng transaction; QueryServiceImpl gọi assembler/mapper sau khi tải dữ liệu |
| BundleMapper | toResponse(...), toItemResponse(...), toSkuResponse(...); MapStruct Spring; chỉ structural mapping từ dữ liệu đã tải/tính |
| Entity types | Product, BundleConfiguration, BundleItem; trạng thái, getter/setter, JPA mapping; không orchestration hoặc gọi service |

Không có BundleSubstitute/BundleSubstituteRequest/BundleSubstituteResponse. BundleRepository ở đây là adapter logic, **không mặc định** JpaRepository<BundleConfiguration,UUID> có thể tự lưu Product và children chưa được JPA mapping. Private Spring Data repository bổ trợ được phép; không expose repository ra module khác. Không cần entity method addItem/publish/updatePrice: service cập nhật bằng getter/setter trong transaction.

CatalogApi công khai hiện có getSkuSummary/getSkuSummaryBySku/getSkuSummaries. Các method getProductNames/getVariantPrices/findProductIdsByName trong UML là thiết kế nội bộ cần adapter hoặc query Catalog, không được tuyên bố đã tồn tại trong public API. Vì combo cùng module Catalog, có thể dùng private repository/query; không thêm public Java contract chỉ để gọi nội bộ. Mapper nhận tên/giá/tổng từ service, không tự gọi CatalogApi/Repository.

Implementation dùng BundlePricingService cho công thức giá và BundleDetailAssembler cho ghép dữ liệu đã tải, tránh vòng phụ thuộc giữa query và evaluation. BundleEvaluationService điều phối preview; không nhân bản các hàm công thức của helper chỉ để khớp tên method minh họa trong UML.

### Processing Flows

| Function | Main flow / alternatives |
|---|---|
| List/Search | Controller -> QueryService -> filter Product/config trước paging -> batch-load items/SKU/name -> tính tổng hiện tại -> Mapper -> ApiResponse; empty=200 |
| Detail | QueryService tải definition -> kiểm admin/storefront visibility -> batch enrich -> Mapper; missing/hidden=404 |
| Create | AdminService validate -> kiểm SKU/category/brand + uniqueness -> tính giá -> tạo Product ACTIVE/config DRAFT/items -> saveAndFlush -> map ->201; lỗi rollback toàn bộ |
| Update | Tải aggregate -> missing/deleted404 -> stale409 -> PUBLISHED409 -> validate nội dung -> thay items/giá/lịch -> flush/version -> map200 |
| Publication | Tải ->404 -> stale409 -> validate transition/no-op -> khi publish kiểm lại current SKU/giá/lịch/ACTIVE -> set status -> flush ->200 |
| Delete | Tải gồm tombstone ->404 -> stale409 -> deleted no-op hoặc set Product DELETED + tăng aggregate version -> flush ->200 không data; không query Sales để cấm soft delete |
| Evaluation | Tải combo hiệu lực ->404 -> optional stale409 -> batch SKU/giá -> batch Inventory -> tính requiredQty/giá/availability ->200 preview, không side effect |

### Inter-Module Java API

| Provider | Consumer | Method/DTO | Transaction | Errors/Compatibility |
|---|---|---|---|---|
| inventory/api/InventoryApi | Catalog BundleEvaluationService | getStockSummaries(Collection<UUID>) -> Map<UUID,StockSummaryDto>; IDs distinct/non-null; DTO chứa variantId, warehouseCode, onHandQty, reservedQty, availableQty, minimumQty | Read-only; failure được cô lập khỏi transaction Catalog | Absent row/runtime failure -> UNAVAILABLE tại consumer; availableQty=null nếu không đọc được; không entity exposure; reuse signature hiện có |

Authorization context của Inventory lookup là request đã qua IAM; lookup không cấp quyền ghi kho. Single-warehouse hiện hành; multi-warehouse selection ngoài scope. Khi implementation không cô lập được lỗi query khỏi transaction Catalog, phải bổ sung adapter read-only REQUIRES_NEW hoặc cơ chế tương đương được kiểm chứng, không chỉ catch exception trong transaction đã rollback-only. Không thay propagation toàn Inventory API một cách ngầm định.

IAM được dùng qua security chain hiện có. Không thêm Sales API trong scope; yêu cầu snapshot Sales là invariant tích hợp tương lai, chưa được chứng minh chỉ bằng test Catalog.

### Domain Event

Not applicable. Không thêm combo event/audit provider giả để đánh dấu hoàn thành; Stock reservation và audit nghiệp vụ ngoài scope.

### Compatibility with Current Runtime

- Hai POST /admin/{id}/publish và /unpublish được thay bằng PATCH /publication-status trong target; cần phối hợp client/cutover trước khi bỏ route cũ.
- Hai POST cũ trả tường minh 404 SYS_004, không thực hiện publish/unpublish; tránh fallback500 của shared handler cho route không còn tồn tại mà không sửa class ngoài bundle.
- Renaming DTO/service và bỏ componentGroups/selections là thay đổi target; việc tài liệu tồn tại không làm backend tự áp dụng.
- Giữ ID bundleProductId, Instant, response naming đang có thay vì tạo bộ ID/field song song theo UML cũ.
- Endpoint evaluation tiếp tục /evaluations; không thêm /availability đồng thời cho cùng use case.
- Không thêm getProductName public API hoặc giả lập integration Inventory/Sales.
- Theo yêu cầu dọn DTO mới nhất, không giữ bridge Java deprecated hoặc DTO legacy. publish/unpublish trở thành private helpers nhận expectedVersion scalar; caller dùng changePublicationStatus. AuthenticationMergeIT ngoài bundle còn import DTO/package cũ được giữ nguyên theo giới hạn phạm vi, nên test compile toàn repository chưa đạt cho đến khi caller đó được cập nhật riêng.

## 6. Concurrency, Transaction and Failure Safety

- Read-only cho list/detail/search/evaluate; writes dùng một PostgreSQL transaction cho Product/config/items. Không trộn network media hoặc thay đổi Redis session vào transaction combo.
- Version thuộc toàn aggregate: sửa Product/items cũng phải làm configuration version tăng. @Version riêng trên configuration không tự phát hiện thay đổi children hoặc Product; adapter phải dirty/force increment phù hợp và kiểm bằng integration test.
- So sánh expectedVersion cùng optimistic write predicate/aggregate lock, không chỉ check rồi save mất bảo vệ. Flush trước thành công; lỗi commit được handler chuẩn hóa, không trả201/200 sớm.
- Hai request cùng version: tối đa một mutation commit, loser409 và rollback toàn bộ. No-op chỉ sau kiểm stale; PUT luôn tăng version.
- DELETE kiểm tombstone để retry current-version no-op; pre-delete version409; không trả version mới qua DELETE. Danh sách/detail loại deleted.
- Unique code/DB constraint xử lý cạnh tranh create; không chỉ exists trước insert. Không ghi lại combo khác khi retry POST; POST create không có idempotency key trong scope, client phải tra cứu mã khi mất response.
- Inventory outage trả UNAVAILABLE/purchasable=false nhưng Catalog read transaction vẫn phải commit/close sạch. Không giả availableQty=0 khi chưa đọc được.
- Evaluation chỉ là snapshot đọc tại evaluatedAt; không giữ lock tồn đến checkout và không bảo đảm giá/tồn ở tương lai. Sales phải đánh giá lại và reserve theo feature riêng.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Create fixed-SKU combo | HTTP + PostgreSQL integration |201/Location; ACTIVE/DRAFT; đúng Product/config/items/version; không SKU/tồn combo |
| AC-02 | Invalid/missing/duplicate/nested/inactive SKU, quantity sai | Parameterized service/API + DB tests |400; không ghi một phần; không coercion quantity |
| AC-03 | Giá và rounding | Unit + API tests | Tổng/discount đúng, scale và overflow được chặn; ví dụ 8m/7.2m -> tiết kiệm800k/10.0% |
| AC-04 | List/search/detail | Repository/controller tests | Filter trước paging, sort ổn định; batch load; empty200; hidden/deleted404 |
| AC-05 | Update và publication lifecycle | Service/HTTP integration | PUBLISHED edit409; PATCH gộp; dates/ACTIVE/current SKU/giá được kiểm lại; no-op giữ version |
| AC-06 | UTC boundary | Clock-controlled tests | from inclusive/until exclusive; publish tương lai hợp lệ; hết hạn không hiển thị |
| AC-07 | Concurrent update/publication/status/delete | Hai transaction PostgreSQL cạnh tranh | Một mutation commit; loser409; children/Product/config rollback nhất quán |
| AC-08 | Soft delete và retry | Persistence/HTTP tests | Giữ rows/code; deleted bị ẩn; stale409/current no-op;200 không data |
| AC-09 | Tồn và nhu cầu | Inventory contract integration | requiredQty nhân đúng; AVAILABLE/UNAVAILABLE; chỉ rõ SKU thiếu; không ghi kho |
| AC-10 | Inventory failure | Failure injection + transaction completion test | UNAVAILABLE/purchasable=false; không UnexpectedRollbackException hoặc auth-code giả |
| AC-11 | AuthN/AuthZ | Real security integration | Missing/refresh/invalid/revoked token bị chặn; non-owner403 quản trị; valid read dùng access JWT |
| AC-12 | DTO/privacy/strict contract | JSON tests | Không cost/token/option/substitute; query lạ/lặp và field lạ400; binding Jackson chung + Bean Validation; envelope đúng |
| AC-13 | Aggregate/schema compatibility | Mapping/migration tests | ID baseline giữ nguyên; không mất Product/items/history; options hiện hữu được xử lý theo rollout đã duyệt |
| AC-14 | Architecture | Boundary/build checks | Không cross-module internal/entity import; mapper không DB/business; interfaces và method thống nhất |
| AC-15 | Regression và cutover | Targeted + full suite + client contract review | Client PATCH/payload mới tương thích rollout; ghi kết quả thực chạy; không suy PASS từ lịch sử |

### Verification trước lần dọn DTO 1.1.0 — 2026-10-27

- Production và test compile thành công. Kiểm chứng cuối phần bundle: **44 Surefire + 42 Failsafe = 86 PASS**, không failure/error/skipped; lệnh `mvnw.cmd -o -Pphase1-it "-Dtest=Bundle*Test" "-Dit.test=Bundle*IT,!BundleImportGuardIT" verify`.
- Bao gồm JWT/Redis/PostgreSQL thật cho quyền SHOP_OWNER/Customer; concurrent writes, rollback, soft delete, PATCH publication, strict timestamp/enum, bảo toàn option rows và lỗi SQL Inventory không làm rollback-only transaction Catalog.
- Full Surefire: 450 tests, 1 failure + 1 error, đều trong QuotationEntityConstraintTest. Full Failsafe chạy riêng do verify dừng ở Surefire: 59 tests, 4 failures + 1 error. **Không tuyên bố toàn suite PASS.**
- Sáu case lỗi đã tái hiện trên source HEAD nguyên trạng: hai case QuotationEntityConstraintTest (FK customer/category format), ba case BundleImportGuardIT (importer chưa bảo vệ combo), một case AuthenticationMergeIT.passwordChangeUsesSameAuthenticatedUuidAccount (không có recovery row).
- Một failure còn lại là AuthenticationMergeIT.loginAccessTokenReadsAndEvaluatesComboWithRealInventory: test cũ kỳ vọng user không có role đọc admin200; implementation mới trả403 theo Owner-only contract. Test này giữ nguyên vì không thuộc class bundle; BundleSecurityIT kiểm riêng hành vi mới và PASS.
- Không sửa ProductBulkExcelImporter, Sales, IAM, shared handler, AuthenticationMergeIT, SQL, cấu hình hoặc dependency. Cần xử lý các test/logic ngoài scope bằng công việc được cho phép riêng trước khi có full-suite gate đạt.
- Evidence local-only: `.agent/bundle-diagrams/bundle-verified.log`, `full-verify.log`, `all-integration.log`, các log `*-baseline.log`; XML reports tại target/surefire-reports và target/failsafe-reports.

Snapshot Sales/media/audit ngoài scope không được đánh dấu PASS từ các AC Catalog. Client rollout và independent Design Review vẫn chưa được chứng nhận.

### Verification dọn DTO 1.1.0 — 2026-10-27

- Bỏ trạng thái tồn không xác định riêng: chỉ AVAILABLE/UNAVAILABLE; thiếu dữ liệu/lỗi Inventory vẫn không được mua, availableQty không bị giả thành0.
- Xóa BundleCoreCreateRequest và BundleVersionRequest; bỏ bridge create và public publish/unpublish cũ. Request publication mới là ChangePublicationStatusRequest; private helper chỉ nhận scalar version.
- Chuyển bốn response/page DTO sang `payload/res`, giữ bảy request DTO và helper JSON trong `payload/req`; không giữ alias ở package cũ.
- **44 unit/contract + 42 integration bundle PASS**, không failure/error/skipped, qua cấu hình test tạm `.agent/bundle-dto-cleanup/pom.xml`. Harness dùng source hiện tại, chỉ giới hạn test compile vào Bundle classes và hai lớp test support hiện có; không sửa POM gốc hoặc source/test ngoài bundle. BundleImportGuardIT vẫn là lỗi importer ngoài phạm vi đã ghi nhận trước đó, không thuộc run đạt này.
- Production compile được kiểm tra riêng bằng POM gốc. `test-compile` toàn repository bị chặn tại AuthenticationMergeIT do còn import DTO/package cũ đã được yêu cầu xóa; giữ nguyên test này theo phạm vi người dùng. Không tuyên bố build/test toàn repository đạt.
- Kiểm tra hash tất cả Java files ngoài bundle không đổi so với trước lần dọn này; không đổi SQL, config hoặc dependency. Evidence tại `.agent/bundle-dto-cleanup/production-compile.log`, `standard-build.log`, `bundle-tests.log` và XML reports trong thư mục target của harness.

### Verification phân trang dùng chung — 2026-10-27

- BundlePageRequest kế thừa `share/dto/PageQuery`; mọi API danh sách bundle và tìm SKU trả `share/dto/PageResponse<T>`. Xóa wrapper BundlePage riêng.
- Dùng `PageableUtils.createPageable` sau validation của Bundle; giữ page mặc định0, size20, giới hạn1..100, thứ tự sort hiện tại và JSON items/page/size/totalElements/totalPages. Không mở thêm query parameter sort.
- Production compile bằng POM gốc PASS; 44 unit/contract + 42 integration Bundle PASS, không failure/error/skipped qua harness đã mô tả ở trên. Log: `.agent/bundle-dto-cleanup/pagination-compile.log`, `pagination-tests.log`.
- Không sửa class shared hoặc Java ngoài các file Bundle của lần chuyển đổi này. Giới hạn test-compile toàn repository do AuthenticationMergeIT dùng DTO cũ vẫn giữ nguyên.

### Đồng bộ cách đọc request với các module khác — 2026-10-27

- Theo yêu cầu người dùng, xóa BundleJsonDeserializers và custom StrictStatus; bỏ toàn bộ @JsonDeserialize trong request Bundle. Dùng Jackson mặc định và Bean Validation hiện có, không sửa shared/config hoặc module khác.
- Các kiểm tra required, Min/Max, PositiveOrZero, DecimalMin/Digits và validation nghiệp vụ tại service được giữ nguyên. Coercion của Jackson mặc định thay thế cam kết strict token trước đây; unknown field vẫn bị từ chối.
- Test request được cập nhật để kiểm tra thiếu field, số âm/ngoài miền, chuỗi không parse được và ngày không hợp lệ; không yêu cầu Jackson từ chối coercion mặc định.
- Production compile PASS; 42 unit/contract + 42 integration Bundle PASS, không failure/error/skipped, qua harness Bundle (vẫn loại BundleImportGuardIT và không sửa AuthenticationMergeIT ngoài phạm vi). Evidence: `.agent/bundle-dto-cleanup/default-jackson-compile.log`, `default-jackson-tests.log`.

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| SKU thay thế/options | Product Owner | Specification | RESOLVED — người dùng yêu cầu bỏ; combo chỉ có SKU cố định |
| Search/service/mapper/DTO | Catalog owner | Specification | RESOLVED — QueryServiceImpl; mapper giữ toResponse; CreateBundleRequest; service xử lý logic/validation |
| PATCH publication và batch lookup | Product Owner / Tech Lead | Specification | RESOLVED — PATCH gộp; lấy tên theo batch |
| Schema identity/aggregate adapter thay vì ID riêng/cascade bắt buộc trong UML | Catalog / DB owner | Design Review | PROPOSED — giữ baseline schema như mục4; đồng bộ UML sau review |
| Mapping quyền Store Owner | IAM owner | Security implementation | RESOLVED trong scope triển khai trực tiếp — dùng role chuẩn SHOP_OWNER hiện có; enforce tại controller bundle, kiểm bằng JWT thật |
| Combo hiện có componentGroups | Product Owner / DB owner | Rollout | Không migration; bảo toàn rows và dùng fixed bundle_items. Kiểm kê tác động client vẫn PENDING trước rollout |
| Route/payload cutover và loại bỏ API cũ | Client owner / Tech Lead | Release | PENDING — chốt lịch tương thích; không âm thầm phá client |
| Draft bắt buộc item, soft delete, precision/rounding và stock preview contract | Product Owner / Tech Lead | Design Review | PROPOSED — lựa chọn cụ thể tại mục3–6 để review, chưa tự cấp approval |
| Ranh giới UC-13 thu gọn với yêu cầu tích hợp Sales/media/audit rộng hơn | Product Owner | Scope acceptance | PENDING — ghi nhận phần ngoài scope; không tuyên bố toàn bộ yêu cầu lịch sử đã hoàn thành |

Không approve Design Review khi còn quyết định cản trở implementation an toàn. Spec là nguồn contract mục tiêu; running code là nguồn hiện trạng. Không triển khai migration/security/breaking API từ tài liệu draft này khi chưa review phần liên quan.
