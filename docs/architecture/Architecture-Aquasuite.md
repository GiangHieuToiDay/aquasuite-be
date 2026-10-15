# AQUASUITE BACKEND ARCHITECTURE (FEATURE-BASED / MODULAR MONOLITH)

> **CHỈ THỊ BẮT BUỘC CHO AI AGENT & DEVELOPERS:**
> Dự án AquaSuite sử dụng kiến trúc **Package-by-Feature (Modular Monolith)** trên nền tảng **Spring Boot 3.x (Java 21)**.
> 1. **TUYỆT ĐỐI CẤM** một module import bất kỳ class nào nằm trong package `internal` của module khác.
> 2. **TUYỆT ĐỐI CẤM** sử dụng `@ManyToOne`, `@OneToMany`, `@OneToOne` nối trực tiếp `@Entity` giữa 2 module khác nhau. Chỉ được lưu khóa ngoại dưới dạng ID thuần (`UUID` hoặc `Long`).
> 3. Mọi giao tiếp giữa các module phải đi qua **Public Facade** (đồng bộ) hoặc **Spring Application Events** (bất đồng bộ) theo ma trận quy định ở Mục 4.

---

## 1. Cấu trúc tổng thể dự án (Root Directory Structure)

```text
com.aquasuite.backend/
├── AquaSuiteApplication.java
├── shared/                             # [GLOBAL] Tầng kỹ thuật dùng chung (Không chứa nghiệp vụ)
│   ├── config/                         # SecurityConfig, AsyncConfig, OpenApiConfig, JacksonConfig
│   ├── exception/                      # GlobalExceptionHandler, BusinessException, ErrorCode
│   ├── dto/                            # ApiResponse<T>, PageResponse<T>, AuditableDto
│   ├── annotation/                     # @Auditable (phục vụ FE-02), @CurrentUser
│   ├──persistence/                     # BaseEntity (id, createdAt, updatedAt, isDeleted)
│   └── utils/                          # MoneyUtils, DateUtils, VietQrGenerator
│
└── modules/                            # [FEATURES] 11 Module nghiệp vụ độc lập
    ├── iam/                            # [FE-01] Account & Access Control (RBAC)
    ├── audit/                          # [FE-02] Audit Trail & Activity History
    ├── catalog/                        # [FE-03] Product, Category, Brand & SKU Variants
    ├── crm/                            # [FE-10] Customer Profile, Construction Projects & Interactions
    ├── aiadvisor/                      # [FE-04] AI Product Consultation & Recommendation
    ├── sales/                          # [FE-05, FE-06] Online Cart, Quotations, Sales Orders, Shifts
    ├── inventory/                      # [FE-07] Stock SKU, Reservation, Purchasing, Suppliers, GRN
    ├── fulfillment/                    # [FE-08] Stock Outbound, Multi-Phase Delivery & Serial Tracking
    ├── finance/                        # [FE-14] Accounting, Vouchers, Receivables/Payables, Reconciliation
    ├── servicedesk/                    # [FE-09, FE-13] Warranty Policies, Claims & Installation/Repair Jobs
    └── intelligence/                   # [FE-11, FE-12] Management Dashboard, Reports & Telegram AI Bot
```

---

## 2. Cấu trúc chuẩn bên trong MỖI Module (Anatomy of a Module)

Mỗi thư mục con trong `modules/{module_name}` là một pháo đài khép kín. Chỉ những gì nằm ở ngoài cùng mới là **Public API**, toàn bộ logic thực thi phải giấu trong `internal/`:

```text
modules/{module_name}/
├── {Module}Facade.java                 # [PUBLIC INTERFACE] Cổng giao tiếp duy nhất cho module khác gọi vào
├── dto/                                # [PUBLIC] Chỉ chứa các DTO cần chia sẻ cho module khác
│   └── ...Dto.java
├── events/                             # [PUBLIC] Các Domain Events (Java Record) bắn ra toàn hệ thống
│   └── ...Event.java
└── internal/                           # [PRIVATE] CẤM MODULE KHÁC IMPORT TỪ PACKAGE NÀY
    ├── controller/                     # REST Controllers (@RestController) nhận request từ Frontend
    ├── service/                        # Implementation của {Module}Facade và các Use Case nội bộ
    │                                   # (Ưu tiên đặt access modifier là package-private, bỏ chữ 'public')
    ├── listener/                       # Các class @Component chứa @TransactionalEventListener
    ├── repository/                     # Spring Data JPA Repositories
    ├── entity/                         # JPA @Entity (Chỉ dùng nội bộ trong module này)
    ├── mapper/                         # MapStruct / Manual Mappers chuyển đổi Entity <-> DTO
    └── payload/                        # Request/Response DTOs chỉ phục vụ riêng cho REST Controller nội bộ
```

---

## 3. Ánh xạ chi tiết 14 Features vào các Modules

### 3.1. Module `iam` (FE-01: Account & Access Control)
*   **Trách nhiệm:** Đăng ký, đăng nhập (JWT), đổi/reset mật khẩu, quản lý tài khoản Staff/Customer, quản lý Role & Permission (RBAC), khóa/mở khóa tài khoản.
*   **Internal Entities:** `AccountEntity`, `RoleEntity`, `PermissionEntity`, `PasswordResetTokenEntity`.
*   **Public API (`IamFacade`):** `validateToken()`, `getAccountSummary(UUID accountId)`, `checkPermission(UUID accountId, String permission)`.
*   **Events phát ra:** `CustomerRegisteredEvent(UUID accountId, String phone, String email, String fullName)`.

### 3.2. Module `audit` (FE-02: Audit Trail & Activity History)
*   **Trách nhiệm:** Tự động ghi log mọi thay đổi nhạy cảm (đổi giá, chỉnh tồn kho, sửa/hủy đơn, điều chỉnh công nợ) kèm giá trị `beforeValue` / `afterValue` (JSONB).
*   **Internal Entities:** `AuditLogEntity`.
*   **Cơ chế đặc biệt:** Cung cấp Spring AOP Aspect lắng nghe annotation `@Auditable(action = "...")` ở tầng `shared` hoặc nhận `AuditLogEvent` từ các module khác. **Không một module nào cần gọi trực tiếp `AuditFacade` để ghi log.**

### 3.3. Module `catalog` (FE-03: Product, Category & Variant Management)
*   **Trách nhiệm:** Quản lý danh mục, thương hiệu, đơn vị tính, sản phẩm gốc (SPU) và biến thể (SKU: giá, màu, kích thước, thông số kỹ thuật, cấu hình quản lý theo Serial `isSerialTracked`, mã chính sách bảo hành mặc định).
*   **Internal Entities:** `CategoryEntity`, `BrandEntity`, `ProductEntity`, `ProductVariantEntity`, `ProductImageEntity`.
*   **Public API (`CatalogFacade`):**
    *   `getVariantBySku(String sku)` / `getVariantsByIds(List<UUID> variantIds)` (Trả về giá bán, tên, `isSerialTracked`, `warrantyPolicyId`).
    *   `searchVariantsForAi(CatalogAiFilterDto filter)` (Phục vụ AI Advisor FE-04).
*   **Events phát ra:** `VariantCreatedEvent`, `VariantPriceChangedEvent` (Kích hoạt Audit FE-02).

### 3.4. Module `crm` (FE-10: Customer, Project & Interaction History)
*   **Trách nhiệm:** Quản lý hồ sơ khách hàng tập trung (Khách lẻ, Nhà thầu, Thợ lắp đặt), sổ địa chỉ giao hàng, hồ sơ **Dự án xây dựng (Construction Projects)**, lịch sử tương tác/chăm sóc khách hàng. Liên kết tài khoản web (`accountId` từ `iam`) với hồ sơ khách hàng có sẵn tại cửa hàng.
*   **Internal Entities:** `CustomerEntity`, `CustomerAddressEntity`, `ConstructionProjectEntity`, `InteractionLogEntity`.
*   **Public API (`CrmFacade`):** `getOrCreateCustomerByPhone(...)`, `getCustomerProfile(UUID customerId)`, `getProjectDetails(UUID projectId)`.

### 3.5. Module `aiadvisor` (FE-04: AI-Powered Product Consultation)
*   **Trách nhiệm:** Chat với khách hàng để thu thập nhu cầu (ngân sách, diện tích phòng tắm, phong cách, điều kiện lắp đặt), gợi ý bộ sản phẩm từ `catalog`, lưu lịch sử tư vấn và gửi **Yêu cầu tư vấn chuyên sâu (Consultation Request)** cho nhân viên Sale.
*   **Internal Entities:** `AiChatSessionEntity`, `AiChatMessageEntity`, `ConsultationRequestEntity`.
*   **Public API (`AiAdvisorFacade`):** `getConsultationRequestDetails(UUID requestId)` (Để module `sales` lấy dữ liệu convert thành Báo giá/Đơn hàng).
*   **Events phát ra:** `ConsultationSubmittedEvent`, `ConsultationConvertedEvent`.

### 3.6. Module `sales` (FE-05 & FE-06: Online Shopping, Quotations, Sales Orders & Shifts)
*   **Trách nhiệm:**
    *   *FE-05 (Online):* Giỏ hàng (`Cart`), khách tự đặt hàng online (COD / VietQR), theo dõi tiến độ đơn, hủy đơn khi chưa xác nhận, gửi yêu cầu hủy/sửa khi đã xác nhận.
    *   *FE-06 (In-store & Project Sales):* Quản lý Báo giá dự án (`Quotation`), chuyển Báo giá thành Đơn hàng (`SalesOrder`), đặt giữ hàng (Layaway/Reservation), lịch giao hàng dự kiến cho dự án, xử lý trả hàng (Sales Return), quản lý Ca làm việc (`SalesShift`), thu chi tiền mặt tại quầy (Petty cash) và bàn giao ca.
*   **Internal Entities:** `CartEntity`, `QuotationEntity`, `QuotationItemEntity`, `SalesOrderEntity`, `SalesOrderItemEntity`, `OrderModificationRequestEntity`, `SalesShiftEntity`.
*   **Trạng thái lưu trên `SalesOrderEntity`:**
    *   `orderStatus`: `DRAFT`, `PENDING_CONFIRMATION`, `CONFIRMED`, `PARTIALLY_DELIVERED`, `COMPLETED`, `CANCELLED`.
    *   `paymentStatus` (Được đồng bộ từ module `finance`): `UNPAID`, `PARTIALLY_PAID`, `PAID`.
*   **Public API (`SalesFacade`):** `getOrderSummary(UUID orderId)`, `getOrderItemsForDelivery(UUID orderId)`.
*   **Events phát ra:**
    *   `OrderConfirmedEvent(UUID orderId, UUID customerId, BigDecimal totalAmount, List<OrderItemDto> items)`
    *   `OrderCancelledEvent(UUID orderId, List<OrderItemDto> items, String reason)`
    *   `OrderReturnedEvent(...)`

### 3.7. Module `inventory` (FE-07: Inventory, Purchasing & Supplier Management)
*   **Trách nhiệm:** Quản lý tồn kho theo từng SKU (`physicalQty`, `reservedQty`, `availableQty = physical - reserved`, `minStockLevel`), lịch sử biến động kho (`StockMovement`), kiểm kho (`StockCount`), điều chỉnh kho có lý do, quản lý Nhà cung cấp (`Supplier`), Đơn đặt hàng nhập (`PurchaseOrder - PO`), Phiếu nhập kho (`GoodsReceiptNote - GRN`), trả hàng nhà cung cấp.
*   **Internal Entities:** `InventoryStockEntity`, `StockReservationEntity`, `StockMovementEntity`, `SupplierEntity`, `PurchaseOrderEntity`, `GoodsReceiptEntity`.
*   **Public API (`InventoryFacade`):**
    *   `checkAvailability(Map<UUID, Integer> variantQtyMap)` (Gọi đồng bộ trước khi tạo đơn).
    *   `reserveStock(UUID orderId, Map<UUID, Integer> variantQtyMap)` (Giữ hàng ngay khi đơn được xác nhận).
*   **Events phát ra:** `GoodsReceiptConfirmedEvent(UUID grnId, UUID supplierId, BigDecimal totalPayable)` (Để `finance` ghi nhận công nợ phải trả NCC), `LowStockAlertEvent`, `StockAdjustedEvent`.

### 3.8. Module `fulfillment` (FE-08: Stock Outbound & Multi-Phase Delivery Management)
*   **Trách nhiệm:** Tách biệt hoàn toàn với `SalesOrder`. Một đơn hàng xác nhận có thể xuất kho nhiều đợt (Multi-phase delivery cho công trình). Theo dõi từng SKU: `orderedQty`, `deliveredQty`, `remainingQty`. Ghi nhận **Serial Number** lúc xuất kho cho sản phẩm có `isSerialTracked = true`. Quản lý phân công Shipper, xác nhận bàn giao hàng, thu hộ COD, xử lý giao thất bại.
*   **Internal Entities:** `DeliveryPlanEntity` (Theo dõi số lượng còn lại của đơn), `DeliveryNoteEntity` (Phiếu xuất kho/giao hàng từng đợt), `DeliveryNoteItemEntity`, `DispatchedSerialEntity`.
*   **Public API (`FulfillmentFacade`):** `getDeliveryProgressByOrder(UUID orderId)`, `verifySerialDelivered(String serialNumber, UUID variantId)`.
*   **Events phát ra:**
    *   `DeliveryBatchDispatchedEvent(UUID deliveryNoteId, UUID orderId, List<DeliveredItemDto> items)` -> Kích hoạt `inventory` trừ `physicalQty` và giải phóng `reservedQty`.
    *   `DeliveryBatchCompletedEvent(UUID deliveryNoteId, UUID orderId, UUID customerId, List<SerialItemDto> serials, BigDecimal codCollectedAmount, boolean isFullyDelivered)` -> Kích hoạt `sales` cập nhật trạng thái đơn, `servicedesk` kích hoạt bảo hành theo Serial, và `finance` ghi nhận tiền thu hộ COD.

### 3.9. Module `finance` (FE-14: Accounting, Receivables, Payables & Reconciliation)
*   **Trách nhiệm:** Quản lý Phiếu thu (`Receipt`), Phiếu chi (`PaymentVoucher`), Công nợ phải thu khách hàng (`CustomerReceivable`), Công nợ phải trả NCC (`SupplierPayable`), đối soát thanh toán chuyển khoản/VietQR (`Reconciliation`), hoàn tiền (`Refund`).
*   **Quy tắc nghiệp vụ cứng (Invariant):**
    *   Theo dõi trên mỗi Receivable của Order: `orderTotal`, `paidAmount`, `outstandingAmount = orderTotal - paidAmount`, `dueDate`.
    *   Tự động tính `paymentStatus`:
        *   `paidAmount == 0` $\rightarrow$ `UNPAID`
        *   `0 < paidAmount < orderTotal` $\rightarrow$ `PARTIALLY_PAID`
        *   `paidAmount >= orderTotal` $\rightarrow$ `PAID`
*   **Internal Entities:** `FinancialTransactionEntity`, `ReceivableEntity`, `PayableEntity`, `ReconciliationRecordEntity`.
*   **Public API (`FinanceFacade`):** `getCustomerDebtSummary(UUID customerId)`, `getOrderPaymentStatus(UUID orderId)`.
*   **Events phát ra:** `OrderPaymentStatusChangedEvent(UUID orderId, PaymentStatus newStatus, BigDecimal paidAmount, BigDecimal outstandingAmount)`.

### 3.10. Module `servicedesk` (FE-09 & FE-13: Warranty & Installation/Repair Jobs)
*   **Trách nhiệm:**
    *   *FE-09 (Warranty):* Quản lý chính sách bảo hành (`WarrantyPolicy`), sổ bảo hành điện tử kích hoạt sau khi giao hàng (`WarrantyRecord` gắn với `customerId`, `orderId`, `variantId`, `serialNumber`), tiếp nhận và xử lý Yêu cầu bảo hành (`WarrantyClaim`).
    *   *FE-13 (Technical Jobs):* Quản lý công việc Lắp đặt & Sửa chữa (`TechnicalJob`), phân công kỹ thuật viên, lịch hẹn, ghi nhận kết quả nghiệm thu tại công trình. Liên kết trực tiếp `TechnicalJob` với `WarrantyClaim` hoặc `SalesOrder`.
*   **Internal Entities:** `WarrantyPolicyEntity`, `WarrantyRecordEntity`, `WarrantyClaimEntity`, `TechnicalJobEntity`.
*   **Public API (`ServiceDeskFacade`):** `getCustomerServiceHistory(UUID customerId)`.

### 3.11. Module `intelligence` (FE-11 & FE-12: Dashboard, Reporting & Telegram AI Bot)
*   **Trách nhiệm:**
    *   *FE-11:* Tổng hợp báo cáo Doanh thu, Chi phí, Lãi/Lỗ (P&L), Tồn kho, Công nợ phải thu/phải trả, Dòng tiền, Cảnh báo vận hành. Xuất Excel/PDF.
    *   *FE-12:* Tích hợp Telegram Bot cho Chủ cửa hàng. Xác thực `telegramChatId` đã liên kết với tài khoản Owner. Dùng LLM (Tool Calling / Function Calling) để chuyển câu hỏi ngôn ngữ tự nhiên thành lệnh gọi vào các hàm Read-only. **CẤM TUYỆT ĐỐI thực hiện lệnh INSERT/UPDATE/DELETE dữ liệu nghiệp vụ từ Telegram.**
*   **Đặc thù kiến trúc (CQRS Read-Model):** Module này được phép sử dụng `JdbcTemplate` / `jOOQ` hoặc các Database Read-Only Views để truy vấn tổng hợp (Aggregation) nhanh mà không làm chậm các module giao dịch, hoặc gọi qua các `*Facade` ở chế độ Read-only.

---

## 4. Ma trận Giao tiếp giữa các Module (Inter-Module Communication Rules)

Để tránh **Circular Dependency** (Module A gọi B, B gọi lại A), dự án quy định rõ khi nào gọi hàm trực tiếp (`Facade`) và khi nào bắn sự kiện (`Event`):

### 4.1. Luồng Đồng bộ (Synchronous Calls via `*Facade`)
Chỉ dùng khi cần **kiểm tra dữ liệu hoặc lấy thông tin ngay lập tức** trong cùng một request:

| Module Gọi (Caller) | Module Đích (Callee) | Qua Interface | Mục đích gọi |
| :--- | :--- | :--- | :--- |
| `sales`, `inventory`, `servicedesk` | `catalog` | `CatalogFacade` | Lấy thông tin SKU, giá bán, kiểm tra cờ `isSerialTracked`. |
| `sales` | `inventory` | `InventoryFacade` | Kiểm tra `availableQty` và gọi `reserveStock()` ngay khi tạo/xác nhận đơn. |
| `sales` | `crm` | `CrmFacade` | Lấy thông tin khách hàng, địa chỉ giao hàng, thông tin dự án. |
| `sales` | `aiadvisor` | `AiAdvisorFacade` | Đọc nội dung `ConsultationRequest` để chuyển đổi thành `Quotation` / `SalesOrder`. |
| `aiadvisor` | `catalog` | `CatalogFacade` | Tìm kiếm sản phẩm phù hợp với tiêu chí phòng tắm/ngân sách của khách. |
| `fulfillment` | `sales` | `SalesFacade` | Lấy danh sách SKU và số lượng đặt trong `SalesOrder` để tạo phiếu xuất kho. |
| `crm` | `sales`, `finance`, `servicedesk` | Các `*Facade` | Tổng hợp lịch sử mua hàng, báo giá, bảo hành và công nợ vào màn hình Chi tiết Khách hàng (FE-10). |

### 4.2. Luồng Bất đồng bộ (Asynchronous / Decoupled via `@TransactionalEventListener`)
Dùng để kích hoạt **hành động hệ quả (Side-effects)** ở module khác sau khi module chính đã commit Database:

```text
[1. LUỒNG CHỐT ĐƠN HÀNG (FE-05, FE-06)]
sales (Confirm Order) 
  └── phát: OrderConfirmedEvent
        ├──► finance (Lắng nghe): Tự động tạo bản ghi Receivable (Công nợ phải thu) với trạng thái UNPAID.
        ├──► fulfillment (Lắng nghe): Khởi tạo DeliveryPlan để kho chuẩn bị xuất hàng theo đợt.
        └──► crm (Lắng nghe): Cập nhật lịch sử giao dịch mới nhất của khách hàng.

[2. LUỒNG THANH TOÁN & ĐỐI SOÁT (FE-06, FE-14)]
finance (Xác nhận Phiếu thu / Đối soát VietQR thành công)
  └── phát: OrderPaymentStatusChangedEvent (UNPAID / PARTIALLY_PAID / PAID)
        ├──► sales (Lắng nghe): Cập nhật cột paymentStatus trên bảng sales_orders.
        └──► crm (Lắng nghe): Cập nhật tổng dư nợ hiện tại của khách hàng.

[3. LUỒNG XUẤT KHO & GIAO HÀNG NHIỀU ĐỢT (FE-07, FE-08, FE-09)]
fulfillment (Tạo phiếu xuất kho & Giao hàng thành công)
  ├── phát: DeliveryBatchDispatchedEvent
  │     └──► inventory (Lắng nghe): Trừ physicalQty, giải phóng reservedQty, ghi StockMovement.
  └── phát: DeliveryBatchCompletedEvent
        ├──► sales (Lắng nghe): Chuyển đơn hàng sang PARTIALLY_DELIVERED hoặc COMPLETED.
        ├──► servicedesk (Lắng nghe): Tự động kích hoạt WarrantyRecord cho từng mã Serial đã giao.
        └──► finance (Lắng nghe): Nếu có thu hộ COD > 0, tạo giao dịch chờ đối soát thu tiền từ Shipper.

[4. LUỒNG NHẬP HÀNG NHÀ CUNG CẤP (FE-07, FE-14)]
inventory (Xác nhận Phiếu nhập kho - GRN)
  └── phát: GoodsReceiptConfirmedEvent
        └──► finance (Lắng nghe): Tự động tạo bản ghi Payable (Công nợ phải trả Nhà cung cấp).
```

---

## 5. Quy tắc Code (Coding Guardrails) cho AI khi triển khai

1.  **Rule 1 - Package Visibility:** Tất cả các class `Service`, `Repository`, `Entity`, `Mapper` bên trong `internal/` **KHÔNG ĐƯỢC** để từ khóa `public` (ngoại trừ `Controller` nếu cần hoặc các method override từ interface `Facade`). Điều này giúp Java Compiler chặn ngay lập tức nếu AI cố tình import chéo.
2.  **Rule 2 - Transaction Boundary:** Khi bắn Event làm thay đổi dữ liệu ở module khác, Listener bắt buộc sử dụng:
    ```java
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handle(SomeEvent event) { ... }
    ```
3.  **Rule 3 - Audit Trail tự động (FE-02):** Bất kỳ hàm Service nào thực hiện: *Đổi giá SKU, Điều chỉnh tồn kho, Sửa/Hủy đơn hàng, Điều chỉnh phiếu thu/chi* bắt buộc phải gắn annotation `@Auditable(action = "...", entityType = "...")` hoặc bắn `AuditLogEvent` kèm snapshot dữ liệu Trước và Sau khi sửa.