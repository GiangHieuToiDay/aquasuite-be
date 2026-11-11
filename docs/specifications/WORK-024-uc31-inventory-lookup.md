# Risk-L Feature Specification — UC-31 Tra cứu & Quản lý Tồn kho

## Metadata

- **Work ID**: `WORK-024`
- **Feature / Use Case**: UC-31 Look up and Manage Inventory Information
- **Version**: SRS v1.4 (doc `1guWQuWFbDLmf32yyiwpBow_JSDFwfUag_Ij1_tJhjp8`)
- **Owner**: Pham Van Nam (NamPham14)
- **Owning Module**: `inventory`
- **Risk Reasons**: inventory, concurrency (đọc tồn chia sẻ với reservation), cross-module (đọc Catalog)
- **Design Review**: PENDING
- **Decision Owner**: Product Owner / Tech Lead

## 1. Goal and Scope

### Problem and Desired Outcome

Hiện module `inventory` chỉ có endpoint xem tồn theo 1 SKU (`GET /{variantId}`), xem thẻ kho và điều chỉnh thủ công. Chủ cửa hàng/Kế toán **chưa có** cách tra cứu toàn bộ tồn kho, lọc tìm, xem danh sách hàng tồn thấp, và **chưa có** chỗ cấu hình ngưỡng tồn an toàn (min/max). Thiếu min/max khiến cảnh báo tồn thấp không hoạt động và tính năng gợi ý mua hàng của Purchasing (`getLowStockSuggestions`) không có đầu vào.

Kết quả mong muốn: một trang "Quản lý Tồn kho" cho phép **tra cứu** (danh sách/tìm kiếm, chi tiết, hàng tồn thấp) và **quản lý ngưỡng** (cấu hình min/max có audit), kèm cảnh báo tồn thấp tự động — tuân thủ GB-23, GB-27.

### In Scope

- **A. List/Search tồn kho**: `GET /api/v1/inventory` — phân trang, lọc `keyword` (tên SP/SKU), `status` (IN_STOCK / LOW_STOCK / OUT_OF_STOCK), `lowStockOnly`; enrich tên/SKU/đơn vị qua `CatalogApi`.
- **B. Chi tiết SKU**: tái dùng `GET /api/v1/inventory/{variantId}` và `GET /{variantId}/movements` (đã có) — chỉ bổ sung `@PreAuthorize`.
- **C. Danh sách tồn thấp**: `GET /api/v1/inventory/low-stock` — toàn kho, định nghĩa dùng chung `Inventory.isLowStock()`.
- **D. Cấu hình ngưỡng**: `PATCH /api/v1/inventory/{variantId}/threshold` — set `minimumQty` + `maximumQty`, validate, ghi audit, trả MSG69.
- **E. Cảnh báo tồn thấp**: listener cho `LowStockAlertEvent` (đã publish, chưa có consumer) → ghi nhận/notification.
- Gắn `@PreAuthorize` cho toàn bộ `InventoryController`.

### Out of Scope

- Nhập kho (UC-32), tạo PO & gợi ý mua hàng (UC-34 — `low-stock-suggestions` đã có bên Purchasing), giữ hàng chi tiết (UC-36 — `StockReservationApi` + `GET /inventory/stock-reservations/variants/{id}` đã có, UC-31 chỉ link sang), kiểm kê/điều chỉnh có duyệt & import/export Excel (UC-38).
- Đa kho: hệ thống dùng 1 kho duy nhất `WH_MAIN`. Không thêm `bin_location`.
- Tự tạo restock request / tự chọn số lượng mua (cấm bởi GB-27).

## 2. Actors and Security

| Actor | Allowed Operations | Required Role/Permission | Data Restrictions |
|---|---|---|---|
| Shop Owner | Tra cứu (A,B,C), **Cấu hình ngưỡng (D)**, điều chỉnh (`/adjust`) | `SHOP_OWNER` | Toàn bộ kho |
| Accountant | Tra cứu (A,B,C) | `ACCOUNTANT` | Toàn bộ kho; KHÔNG cấu hình ngưỡng, KHÔNG adjust |

- `PATCH /threshold` **chỉ** `SHOP_OWNER` (chốt 2026-11-12), đồng nhất với ma trận SRS ("Minimum stock level Configure: chỉ Shop Owner").
- Thay đổi ngưỡng là hành động nhạy cảm (ảnh hưởng cảnh báo & gợi ý mua) → ghi audit actor + before/after (tinh thần GB-26/GB-06).
- Không log dữ liệu nhạy cảm; không có secret.

## 3. Business Rules

1. **BR-01 (GB-23)**: Tồn theo SKU gồm `onHandQty`, `reservedQty`, `availableQty = onHandQty − reservedQty`, `minimumQty`, và lịch sử thẻ kho.
2. **BR-02**: "Tồn thấp" khi `minimumQty > 0 && availableQty ≤ minimumQty` (đúng `Inventory.isLowStock()`). `minimumQty = 0` nghĩa là không theo dõi ngưỡng → không cảnh báo.
3. **BR-03**: Trạng thái hiển thị: `OUT_OF_STOCK` khi `availableQty ≤ 0`; `LOW_STOCK` khi thỏa BR-02 và available > 0; còn lại `IN_STOCK`.
4. **BR-04 (validate ngưỡng)**: `minimumQty ≥ 0`; nếu có `maximumQty` thì `maximumQty ≥ minimumQty`. `maximumQty` được phép null.
5. **BR-05 (GB-27)**: UC-31 chỉ sinh cảnh báo/notification; KHÔNG tự tạo PO hay chọn số lượng mua.
6. **BR-06 (GB-25)**: UC-31 KHÔNG thay đổi tồn vật lý (`onHandQty`). Chỉ đọc + cấu hình ngưỡng. (Việc tăng/giảm tồn thuộc goods receipt/dispatch/adjust/stock-count.)
7. **BR-07**: Định nghĩa low-stock của UC-31 và của Purchasing `getLowStockSuggestions` phải đồng nhất để không lệch cảnh báo.

Không có lifecycle/state machine riêng.

## 4. Data and Migration Decisions

- Owned entity/table: `inventories` (đã có `minimum_qty`, `maximum_qty`), `stock_movements` (đọc). **Không thay đổi schema** — min/max đã tồn tại.
- Cross-module references bằng scalar `variantId`; đọc tên/SKU qua `CatalogApi` (không JPA chéo module).
- Migration: **Not applicable** (không thêm cột/bảng). Tùy chọn: có thể thêm index phụ trợ truy vấn low-stock nếu đo thấy chậm — để lại, không làm trong slice đầu.
- Backfill/rollback: Not applicable.

## 5. Contracts

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| `GET /api/v1/inventory` | `SHOP_OWNER`,`ACCOUNTANT` | query: `keyword?`, `status?` (IN_STOCK/LOW_STOCK/OUT_OF_STOCK), `lowStockOnly?`, `page,size,sort` | `ApiResponse<Page<InventoryListItemResponse>>` | 401/403 |
| `GET /api/v1/inventory/low-stock` | `SHOP_OWNER`,`ACCOUNTANT` | query: `page,size,sort` | `ApiResponse<Page<InventoryListItemResponse>>` (chỉ dòng low-stock) | 401/403 |
| `GET /api/v1/inventory/{variantId}` (đã có) | `SHOP_OWNER`,`ACCOUNTANT` | path `variantId` | `ApiResponse<StockSummaryDto>` | 404 INVENTORY_NOT_FOUND |
| `GET /api/v1/inventory/{variantId}/movements` (đã có) | `SHOP_OWNER`,`ACCOUNTANT` | path + pageable | `ApiResponse<Page<StockMovement>>` | 401/403 |
| `PATCH /api/v1/inventory/{variantId}/threshold` | `SHOP_OWNER` | body `{minimumQty, maximumQty?}`; BR-04 | `ApiResponse<StockSummaryDto>` + msg MSG69 | 400 INVALID_THRESHOLD, 404 INVENTORY_NOT_FOUND |
| `POST /api/v1/inventory/adjust` (đã có) | `SHOP_OWNER` | — | — | — |

`InventoryListItemResponse`: `variantId, sku, productName, unit, onHandQty, reservedQty, availableQty, minimumQty, maximumQty, status, isLowStock`.

### Inter-Module Java API

| Provider | Consumer | Method/DTO | Transaction | Errors/Compatibility |
|---|---|---|---|---|
| `CatalogApi` | inventory | `getSkuSummaries(variantIds)` enrich list | readOnly | Không đổi contract Catalog |
| `InventoryApi` | — | KHÔNG đổi public contract | — | Thêm nội bộ InventoryService; không breaking |

### Domain Event

| Event | Publisher/Consumers | Payload | Timing | Idempotency/Failure Handling |
|---|---|---|---|---|
| `LowStockAlertEvent` (đã có) | Publisher: InventoryServiceImpl/StockReservationService; **Consumer mới**: listener UC-31 | `variantId, onHandQty, availableQty, minimumQty` | Sau commit thay đổi tồn | Listener best-effort, lỗi listener không rollback nghiệp vụ gốc; tránh spam trùng |

## 6. Concurrency, Transaction and Failure Safety

- Các endpoint tra cứu `@Transactional(readOnly=true)`, không khóa.
- `PATCH /threshold`: đọc `Inventory` (khóa dòng nếu cần nhất quán), set min/max, lưu trong 1 transaction; chỉ sửa trường ngưỡng, KHÔNG đụng `onHandQty/reservedQty` → không xung đột với reservation/goods receipt.
- `availableQty` phái sinh từ `onHandQty − reservedQty`; reservation service vẫn là bên duy nhất ghi `reservedQty` → UC-31 chỉ đọc, không tạo race.
- Listener low-stock chạy sau khi giao dịch gốc commit; thất bại chỉ mất cảnh báo, không ảnh hưởng tồn.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | `GET /inventory` trả danh sách phân trang, enrich tên/SKU, đúng onHand/reserved/available | integration test | 200, dữ liệu khớp |
| AC-02 | Lọc `keyword`, `status`, `lowStockOnly` trả đúng tập hợp | integration test | Tập kết quả đúng |
| AC-03 | `GET /inventory/low-stock` chỉ trả SKU thỏa BR-02 | integration test | Khớp định nghĩa isLowStock |
| AC-04 | `PATCH /threshold` cập nhật min/max hợp lệ, trả MSG69, ghi audit before/after | integration + audit test | 200, audit có bản ghi |
| AC-05 | `PATCH /threshold` từ chối min<0 hoặc max<min | unit test | 400 INVALID_THRESHOLD |
| AC-06 | `PATCH /threshold` bị 403 với `ACCOUNTANT`/vai trò khác | security test | 403 |
| AC-07 | Tra cứu bị 403 với vai trò ngoài SHOP_OWNER/ACCOUNTANT | security test | 403 |
| AC-08 | Sau khi set min và available tụt ≤ min, phát `LowStockAlertEvent` và listener ghi nhận | integration test | Event + bản ghi cảnh báo |
| AC-09 | Định nghĩa low-stock đồng nhất giữa inventory list và Purchasing suggestions | unit test đối chiếu | Cùng kết quả cho cùng dữ liệu |

Verification commands: `./mvnw -o test -Dtest=Inventory*` (targeted); full suite ghi kết quả hoặc lý do N/A.

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| Quyền `PATCH /threshold` | Product Owner | Design Review | **RESOLVED 2026-11-12**: chỉ `SHOP_OWNER` |
| Có cần index low-stock | Tech Lead | implementation | Hoãn — chỉ thêm nếu đo chậm |
| Audit qua module `audit` hay log nội bộ | Tech Lead | implementation | Kiểm tra `module/audit` khi code; mặc định dùng cơ chế audit sẵn có |
