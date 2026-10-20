# Backend Architecture - AquaSuite Modular Monolith

## System State and Architecture Target

Tài liệu này xác lập tiêu chuẩn kiến trúc backend cho hệ thống AquaSuite, phân định ranh giới giữa mã nguồn đang chạy thực tế và thiết kế kiến trúc đích.

### Current System Baseline

- **Runtime & Framework**: Java 21, Spring Boot 3.5.16 (quản lý qua `pom.xml`, BOM quản lý Spring Framework 6.2.x và Spring Security 6.5.x).
- **Base Package**: Toàn bộ mã nguồn hiện tại nằm dưới gói `com.aquasuite.ims`.
- **Database Baseline**: PostgreSQL là hệ thống cơ sở dữ liệu chuẩn duy nhất.
- **Existing Components**:
  - `com.aquasuite.ims.AquasuiteBeApplication`: Main class khởi chạy ứng dụng.
  - `com.aquasuite.ims.share.dto.ApiResponse<T>`: Cấu trúc response chuẩn cho HTTP API (`success`, `code`, `message`, `data`, `errors`, `timestamp`).
  - `com.aquasuite.ims.share.dto.FieldErrorDetail`: Chi tiết lỗi validation từng trường (`field`, `message`).
  - `com.aquasuite.ims.share.exception.GlobalExceptionHandler`: Bộ xử lý lỗi tập trung qua `@RestControllerAdvice` (`AppException`, `MethodArgumentNotValidException`, `AccessDeniedException`, `DataIntegrityViolationException`, `Exception`).
  - `com.aquasuite.ims.share.exception.AppException`: Ngoại lệ nghiệp vụ kế thừa `RuntimeException`, mang theo `BaseErrorCode`.
  - `com.aquasuite.ims.share.exception.CommonErrorCode`: Enum mã lỗi hệ thống (`SYS_001` đến `SYS_004`, `AUTH_001`, `AUTH_002`, `DB_001`).
- **Implementation State**: Các module nghiệp vụ được cấu trúc dưới `com.aquasuite.ims.module.<module_name>`. Workspace đã có core/đăng bán combo và đang tích hợp authentication v2 từ nhánh SCRUM-1: User UUID, JWT và Redis session. Yêu cầu người dùng mới áp dụng JWT cho mọi API ngoài POST login/refresh, bao gồm các API combo trước đây gọi là public. Trạng thái kiểm chứng nằm trong plan UC-13 revision 0.11.0; không suy ra đã deploy hoặc nghiệm thu toàn hệ thống.

### Target Architecture

Kiến trúc mục tiêu chuẩn hóa là **Modular Monolith** đặt trực tiếp dưới base package `com.aquasuite.ims`:

```text
com.aquasuite.ims
├── share/                              # Cấu hình kỹ thuật và giao ước hạ tầng dùng chung (com.aquasuite.ims.share)
│   ├── config/                         # Cấu hình framework (Security, OpenAPI, Jackson)
│   ├── exception/                      # Quản lý lỗi hệ thống và base exceptions
│   ├── dto/                            # Response envelopes kỹ thuật dùng chung
│   └── util/                           # Tiện ích kỹ thuật thuần túy
└── module/                             # Các module nghiệp vụ chuẩn hóa (com.aquasuite.ims.module)
    └── <module_name>/
        ├── api/                        # PUBLIC CONTRACT: Interface, DTO, Event công khai cho module khác
        │   ├── <Module>Api.java        # Interface giao tiếp đồng bộ
        │   ├── dto/                    # DTO công khai dùng làm tham số hoặc kết quả trả về
        │   └── event/                  # Domain Events công khai (Java Record)
        └── internal/                   # PRIVATE IMPLEMENTATION: Cấm module khác import trực tiếp
            ├── controller/             # REST Controller tiếp nhận HTTP request từ client
            ├── service/                # Business logic và hiện thực của <Module>Api
            │   └── impl/
            ├── listener/               # Event listeners xử lý domain events
            ├── repository/             # Spring Data JPA Repositories
            ├── entity/                 # JPA Entities nội bộ
            ├── mapper/                 # Bộ chuyển đổi Entity <-> DTO nội bộ
            └── payload/                # Request/Response body phục vụ riêng REST Controller
```

---

## Package Structure and Visibility Rules

### Share Layer Boundaries (`com.aquasuite.ims.share`)

- Gói `com.aquasuite.ims.share` chỉ chứa các thành phần hạ tầng kỹ thuật dùng chung không phụ thuộc nghiệp vụ.
- **Tuyệt đối cấm** đặt business logic, domain entities, repository hoặc rule nghiệp vụ của bất kỳ module nào vào `share`.
- Nếu hai module có nhu cầu chia sẻ dữ liệu hoặc gọi logic của nhau, việc trao đổi phải thông qua public contract tại `module/<module_name>/api/`, không chuyển dữ liệu đó thành đối tượng dùng chung trong `share`.

### Module Encapsulation and Import Rules

1. **Ranh giới Public Contract**: Module A chỉ được phép import các class nằm trong gói `module/<module_b>/api/` của Module B.
2. **Cấm truy cập Internal**: Tuyệt đối cấm Module A import bất kỳ class nào nằm trong gói `module/<module_b>/internal/` của Module B (bao gồm controller, service nội bộ, repository, entity, mapper, payload). Các type trong `internal/` mang tính private về mặt kiến trúc; dù có khai báo `public` theo yêu cầu của Spring/JPA thì vẫn không phải là public contract.
3. **Cấm liên kết JPA Entity chéo Module**: Không sử dụng `@ManyToOne`, `@OneToMany`, `@OneToOne`, `@ManyToMany` trực tiếp giữa hai `@Entity` thuộc hai module khác nhau. Quan hệ chéo module trong mô hình đối tượng Java chỉ được lưu dưới dạng ID thuần (`UUID`, `Long` hoặc `String`).
4. **Hợp đồng không dùng Entity**: Public contract không được nhận vào hoặc trả về JPA Entity; chỉ sử dụng primitive/scalar types, public DTOs hoặc event records thuộc `api/`.
5. **Ràng buộc khóa ngoại ở mức Database**: Việc cấm JPA entity relationship chéo module không đồng nghĩa với việc cấm Foreign Key ở mức database schema trên PostgreSQL. Việc thiết lập khóa ngoại, chỉ mục (index) hoặc trigger toàn vẹn dữ liệu trong cơ sở dữ liệu sẽ do schema design của từng use case quyết định để đảm bảo toàn vẹn dữ liệu vật lý.
6. **Kiểm tra ranh giới bằng ArchUnit**: Workspace đã có dependency ArchUnit và `BundleCoreBoundaryTest` kiểm tra các class combo/audit thuộc phạm vi UC-13. Không coi kiểm tra có phạm vi này là bằng chứng toàn bộ mã legacy đã tuân thủ kiến trúc. Contract actor mới của IAM nằm trong `iam/api`; không thêm import chéo `internal`.

---

## Domain and Feature Mapping

Hệ thống AquaSuite bao gồm 11 module nghiệp vụ ứng viên, ánh xạ tới 14 mã yêu cầu chức năng (FE-01 đến FE-14). Dưới đây là bảng phân định trách nhiệm sở hữu dữ liệu và trạng thái đề xuất (mọi hợp đồng công khai chi tiết đều ở trạng thái `TBD` cho đến khi có Feature Spec được phê duyệt):

| Requirement Code | Feature / Module | Owning Module | Owned Data & Responsibilities | Expected Consumers | Required Contracts | Decision Status |
|---|---|---|---|---|---|---|
| **FE-01** | Account & Access Control | `iam` | Tài khoản nhân viên/khách hàng, Roles, Permissions, Refresh Tokens. | All features, `crm` | Public API xác thực/phân quyền, DTO tóm tắt account, `CustomerRegisteredEvent`. | Candidate domain; contract details TBD; requires approved Feature Spec |
| **FE-02** | Audit Trail & Activity History | `audit` | Bản ghi nhật ký thay đổi dữ liệu nhạy cảm (`beforeValue`, `afterValue`). | Toàn hệ thống (qua AOP/Event) | Ghi nhận audit tự động, không yêu cầu module khác gọi trực tiếp API nội bộ. | Candidate domain; schema log TBD; requires approved Feature Spec |
| **FE-03** | Product, Category & SKU Variants | `catalog` | SPU, SKU biến thể, danh mục, thương hiệu, đơn vị tính, cờ quản lý serial (`isSerialTracked`), mã chính sách bảo hành mặc định. | `sales`, `inventory`, `servicedesk`, `aiadvisor` | `CatalogApi` (tra cứu SKU, giá niêm yết, thông tin bảo hành, lọc AI), `VariantPriceChangedEvent`. | Candidate domain; interface methods TBD; requires approved Feature Spec |
| **FE-04** | AI Product Consultation | `aiadvisor` | Lịch sử chat AI, tiêu chí tư vấn (ngân sách, phong cách), `ConsultationRequest`. | `sales` | `AiAdvisorApi` (lấy chi tiết yêu cầu tư vấn để chuyển thành báo giá/đơn hàng). | Candidate domain; signature TBD; requires approved Feature Spec |
| **FE-05, FE-06** | Online Shopping, Quotations, Sales Orders & Shifts | `sales` | Giỏ hàng, Báo giá (`Quotation`), Đơn hàng (`SalesOrder`), Ca bán hàng (`SalesShift`), Thu chi tại quầy. | `fulfillment`, `finance`, `crm`, `servicedesk` | `SalesApi` (thông tin đơn hàng, danh sách SKU), `OrderConfirmedEvent`, `OrderCancelledEvent`. | Candidate domain; cross-call review TBD; requires approved Feature Spec |
| **FE-07** | Inventory, Purchasing & Suppliers | `inventory` | Tồn kho SKU (`physicalQty`, `reservedQty`), phiếu kiểm kho, Nhà cung cấp, Đơn nhập hàng (PO), Phiếu nhập kho (GRN). | `sales`, `fulfillment`, `finance` | `InventoryApi` (kiểm tra tồn, giữ hàng `reserveStock`), `GoodsReceiptConfirmedEvent`. | Candidate domain; atomicity rule TBD; requires approved Feature Spec |
| **FE-08** | Stock Outbound & Multi-Phase Delivery | `fulfillment` | Kế hoạch giao hàng (`DeliveryPlan`), Phiếu xuất kho từng đợt (`DeliveryNote`), Serial xuất kho. | `sales`, `inventory`, `finance`, `servicedesk` | `FulfillmentApi` (tiến độ giao hàng, xác thực serial), `DeliveryBatchDispatchedEvent`, `DeliveryBatchCompletedEvent`. | Candidate domain; event fields TBD; requires approved Feature Spec |
| **FE-09, FE-13** | Warranty & Technical Jobs | `servicedesk` | Chính sách bảo hành, Bản ghi bảo hành điện tử (`WarrantyRecord`), Yêu cầu bảo hành, Phiếu công việc kỹ thuật (`TechnicalJob`). | `crm`, Khách hàng, Kỹ thuật viên | `ServiceDeskApi` (lịch sử bảo hành theo khách hàng/serial), nhận sự kiện kích hoạt bảo hành từ `fulfillment`. | Candidate domain; schema TBD; requires approved Feature Spec |
| **FE-10** | Customer Profile & Projects | `crm` | Hồ sơ khách hàng tập trung, Sổ địa chỉ, Dự án công trình xây dựng (`ConstructionProject`), Nhật ký tương tác. | `sales`, `servicedesk`, `finance` | `CrmApi` (tra cứu/tạo khách hàng theo SĐT, thông tin dự án, địa chỉ giao). | Candidate domain; query model TBD; requires approved Feature Spec |
| **FE-11, FE-12** | Dashboard, Reports & Telegram Bot | `intelligence` | Tổng hợp báo cáo P&L, tồn kho, công nợ, cấu hình Telegram Chatbot phục vụ chủ cửa hàng (Chỉ đọc). | Chủ doanh nghiệp, Quản lý | CQRS Read-Model / Views truy vấn dữ liệu chỉ đọc, không gọi lệnh cập nhật dữ liệu. | Candidate domain; queries TBD; requires approved Feature Spec |
| **FE-14** | Accounting, Receivables & Payables | `finance` | Phiếu thu, Phiếu chi, Công nợ phải thu (`Receivable`), Công nợ phải trả (`Payable`), Đối soát chuyển khoản. | `sales`, `crm`, `inventory`, Kế toán | `FinanceApi` (dư nợ khách hàng, trạng thái thanh toán đơn), `OrderPaymentStatusChangedEvent`. | Candidate domain; reconciliation TBD; requires approved Feature Spec |

> [!NOTE]
> Các khái niệm như `user` hay `booking` trong một số tài liệu ví dụ chỉ mang tính minh họa mẫu kiến trúc, không phải module nghiệp vụ của AquaSuite.

---

## Inter-Module Communication Matrix

### Synchronous Communication (`module/<module_name>/api`)

Giao tiếp đồng bộ thông qua Interface trong `api/` chỉ được sử dụng khi use case của Caller đòi hỏi **kết quả trả về tức thời** để tiếp tục xử lý nghiệp vụ:

| Caller Module | Target Module | Contract Interface | Business Purpose | Dependency Constraint |
|---|---|---|---|---|
| `sales` | `catalog` | `CatalogApi` | Lấy giá niêm yết, thông tin SKU, kiểm tra cấu hình `isSerialTracked`. | Chỉ đọc, không thay đổi trạng thái |
| `sales` | `inventory` | `InventoryApi` | Kiểm tra tồn khả dụng (`availableQty`) và yêu cầu đặt trước (`reserveStock`). | Tham gia quy trình xác nhận đơn (Decision TBD) |
| `sales` | `crm` | `CrmApi` | Tra cứu hồ sơ khách hàng, địa chỉ giao hàng, thông tin dự án xây dựng. | Chỉ đọc |
| `sales` | `aiadvisor` | `AiAdvisorApi` | Lấy thông tin yêu cầu tư vấn đã nộp để lập báo giá hoặc tạo đơn hàng. | Chỉ đọc |
| `aiadvisor` | `catalog` | `CatalogApi` | Lọc tìm kiếm sản phẩm theo tiêu chí ngân sách, kích thước, phong cách. | Chỉ đọc |
| `fulfillment` | `sales` | `SalesApi` | Lấy chi tiết các hạng mục và số lượng đặt trong `SalesOrder` để lập phiếu xuất kho. | Chỉ đọc |
| `servicedesk` | `catalog` | `CatalogApi` | Tra cứu chính sách bảo hành tiêu chuẩn gắn với biến thể sản phẩm. | Chỉ đọc |

### Elimination of Direct Synchronous Cycle (`sales` <-> `crm`)

Tài liệu kiến trúc cũ tồn tại mâu thuẫn phụ thuộc hai chiều: `sales -> crm` (lấy thông tin khách hàng) và đồng thời `crm -> sales` (lấy lịch sử đơn hàng để hiển thị màn hình 360 độ khách hàng).

**Quy tắc chuẩn hóa:**
- Loại bỏ hoàn toàn chiều gọi đồng bộ trực tiếp từ `crm` sang `sales`.
- Để phục vụ màn hình tổng hợp thông tin khách hàng (bao gồm đơn hàng từ `sales`, công nợ từ `finance`, bảo hành từ `servicedesk`):
  - **Tùy chọn A (Composition / Query Service)**: Tạo một query use case riêng biệt (ví dụ `CustomerProfileAggregationQuery`) đứng bên trên các feature để điều phối truy vấn, hoặc giải quyết ở tầng BFF / Read-Model.
  - **Tùy chọn B (Event-Driven Read Cache)**: `crm` lắng nghe các Domain Event (`OrderConfirmedEvent`, `OrderPaymentStatusChangedEvent`) để duy trì bản sao lịch sử tóm tắt nội bộ.
  - **Quyết định (Decision Gate)**: Đánh dấu `TBD`. Quyết định chính thức thuộc về Lead Architect khi bắt tay triển khai chi tiết tính năng FE-10.

### Decoupled Communication via Domain Events (Eventual Consistency)

Sự kiện ứng dụng (Domain Events) được sử dụng để kích hoạt các tác vụ hệ quả (side-effects) khi **tính nhất quán sau cùng (eventual consistency)** là chấp nhận được.

```text
[LUỒNG XÁC NHẬN ĐƠN HÀNG (FE-05, FE-06)]
sales (Confirm Order)
  └── phát: OrderConfirmedEvent
        ├──► finance: Tạo bản ghi Receivable (Công nợ phải thu) với trạng thái UNPAID
        ├──► fulfillment: Khởi tạo DeliveryPlan chuẩn bị xuất hàng theo tiến độ
        └──► crm: Cập nhật thời điểm giao dịch gần nhất của khách hàng

[LUỒNG THANH TOÁN & ĐỐI SOÁT (FE-06, FE-14)]
finance (Xác nhận Phiếu thu / Đối soát thành công)
  └── phát: OrderPaymentStatusChangedEvent
        ├──► sales: Cập nhật paymentStatus trên SalesOrder
        └──► crm: Cập nhật công nợ lũy kế của khách hàng

[LUỒNG XUẤT KHO & GIAO HÀNG ĐA ĐỢT (FE-07, FE-08, FE-09)]
fulfillment (Xuất kho & Hoàn thành đợt giao)
  ├── phát: DeliveryBatchDispatchedEvent
  │     └──► inventory: Trừ physicalQty, giải phóng reservedQty, ghi thẻ kho
  └── phát: DeliveryBatchCompletedEvent
        ├──► sales: Chuyển trạng thái đơn (PARTIALLY_DELIVERED hoặc COMPLETED)
        ├──► servicedesk: Tự động kích hoạt sổ bảo hành cho các Serial đã bàn giao
        └──► finance: Ghi nhận chứng từ thu hộ COD nếu có

[LUỒNG NHẬP KHO NHÀ CUNG CẤP (FE-07, FE-14)]
inventory (Xác nhận Phiếu nhập kho - GRN)
  └── phát: GoodsReceiptConfirmedEvent
        └──► finance: Tạo bản ghi Payable (Công nợ phải trả Nhà cung cấp)
```

> [!WARNING]
> Cần phân biệt rõ giữa **chế độ thực thi luồng** và **giai đoạn kích hoạt transaction**:
> 1. **Chế độ thực thi luồng**: Mặc định trong Spring Framework, Application Events chạy **đồng bộ trên cùng thread của Publisher**. Việc thực thi bất đồng bộ đa luồng (multi-threaded async) đòi hỏi cấu hình `TaskExecutor` và `@Async` riêng biệt.
> 2. **Giai đoạn Transaction**: Annotation `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)` chỉ bảo đảm listener được kích hoạt sau khi transaction nguồn commit thành công. Nó **không biến event thành hàng đợi tin nhắn bền vững (durable message queue)**. Nếu tiến trình bị tắt hoặc listener thất bại, event có thể bị mất nếu không có hàng đợi/outbox chuyên dụng.
> Do đó, mọi Event Contract bắt buộc phải chỉ định cơ chế xử lý lỗi, trạng thái chờ (pending state), và tính idempotent khi listener thực thi lại.

### Open Consistency Decision Gates

1. **Xác nhận Đơn hàng và Giữ Tồn kho (`sales` & `inventory`)**:
   - *Vấn đề*: Khi khách hàng xác nhận đơn hàng, việc trừ/giữ tồn kho có bắt buộc nằm trong cùng một database transaction (ACID tức thời) hay tách rời qua event (chấp nhận đơn ở trạng thái `PENDING_RESERVATION` rồi xác nhận sau)?
   - *Trạng thái*: `TBD` (Cần Product Owner và Lead Architect quyết định dựa trên yêu cầu chịu tải bán hàng online và bán tại quầy).
2. **Kích hoạt bảo hành khi giao hàng thành công (`fulfillment` & `servicedesk`)**:
   - *Vấn đề*: Khi giao hàng xong, nếu listener kích hoạt bảo hành điện tử thất bại thì đơn hàng có được xem là giao thành công không?
   - *Trạng thái*: `TBD` (Mặc định cho phép eventual consistency kèm bảng ghi nhận retry tác vụ thất bại).

---

## Coding Guardrails and Invariants

1. **Guardrail 1 - Package Visibility**:
   - Chỉ các Interface, DTO, Event đặt trong `module/<module_name>/api/` mới được xem là public module contract.
   - Các class trong `internal/` (Entity, Repository, Service Implementation, Mapper, Controller) phải được thiết kế để không cho phép bên ngoài truy cập. Khi triển khai các subpackage trong `internal/`, lập trình viên phải duy trì tính đóng gói và không mở rộng quyền truy cập cho module khác.
2. **Guardrail 2 - Transaction Boundaries & Event Handling**:
   - Khi phát Domain Event, Publisher phải bảo đảm payload chứa thông tin tối thiểu cần thiết và mã định danh nghiệp vụ.
   - Khi Consumer nhận Event qua `@TransactionalEventListener(phase = AFTER_COMMIT)`, tác vụ ghi dữ liệu phải mở transaction mới (`@Transactional(propagation = Propagation.REQUIRES_NEW)`) và có cơ chế bắt lỗi để không làm treo luồng chính.
3. **Guardrail 3 - Idempotency & Deduplication**:
   - Mọi Consumer lắng nghe Event hoặc API xử lý thanh toán/tồn kho phải kiểm tra tính lặp lại (idempotency key / event ID) trước khi thay đổi trạng thái dữ liệu.
4. **Guardrail 4 - Audit Trail Invariant (FE-02)**:
   - Các thao tác nhạy cảm (thay đổi giá bán, chỉnh sửa tồn kho, hủy/sửa đơn hàng, điều chỉnh công nợ) bắt buộc phải phát sinh bản ghi kiểm toán kèm theo dữ liệu trước (`beforeValue`) và sau (`afterValue`).
