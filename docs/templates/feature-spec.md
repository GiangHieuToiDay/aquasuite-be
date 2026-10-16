# Feature Specification Template

> [!NOTE]
> Mẫu tài liệu đặc tả tính năng này áp dụng cho mọi yêu cầu phát triển mới hoặc thay đổi logic nghiệp vụ trong AquaSuite backend. Mỗi Feature Spec phải được kiểm duyệt và phê duyệt trước khi lập kế hoạch triển khai.

---

## Metadata and Governance

- **Feature ID**: `FE-XX`
- **Feature Name**: `<Tên tính năng>`
- **Version**: `1.0.0`
- **Owner / Author**: `<Tên kỹ sư sở hữu / Đội ngũ>`
- **Owning Module**: `<iam | catalog | sales | inventory | fulfillment | finance | crm | servicedesk | intelligence | aiadvisor | audit>`
- **Repository Scope**: `BE (aquasuite-be)`
- **Target Package**: `com.aquasuite.be.features.<feature_name>`
- **Risk Level**: `<LOW | MEDIUM | HIGH | CRITICAL>`
  - *Risk-Based Depth Rule*: Mức độ rủi ro quyết định độ sâu của tài liệu. Các tính năng mức HIGH/CRITICAL (liên quan trực tiếp đến tiền tệ, công nợ, trừ tồn kho, tính toán thuế, giao hàng nhiều đợt) bắt buộc phải có đầy đủ phần phân tích Concurrency, Transaction Boundary, Failure Recovery và Ma trận AC-to-Test chi tiết.

---

## 1. Business Context and Objectives

### Problem Statement
<Mô tả ngắn gọn vấn đề kinh doanh cần giải quyết, bối cảnh thực tế tại cửa hàng hoặc hệ thống online.>

### Business Goals & Scope
- <Mục tiêu 1: Cung cấp khả năng ...>
- <Mục tiêu 2: Tự động hóa ...>

### Out of Scope
- <Các khía cạnh hoặc luồng nghiệp vụ không xử lý trong phiên bản này để tránh phình phạm vi.>

---

## 2. Actors, Permissions, and Security Model

### Actor and Authorization Matrix

| Actor | Role Code | Identity Source | Authentication Context | Authorized Operations | Required Permissions |
|---|---|---|---|---|---|
| Khách hàng Online | `ROLE_CUSTOMER` | JWT Bearer Token (IAM) | Customer Claims | Tạo đơn hàng, xem đơn cá nhân | `order:create`, `order:read:self` |
| Nhân viên Bán hàng | `ROLE_STAFF` | JWT Bearer Token (IAM) | Staff Claims | Lập báo giá, xác nhận đơn tại quầy | `quotation:create`, `order:confirm` |
| Quản trị viên | `ROLE_ADMIN` | JWT Bearer Token (IAM) | Admin Claims | Toàn quyền cấu hình | `system:admin` |
| Hệ thống Nội bộ | `SYSTEM` | Spring Application Context | Service Call | Lắng nghe event, tác vụ batch | Nội bộ |

### Data Protection and Sensitive Fields
- **Phân loại dữ liệu nhạy cảm**: Xác định các trường nhạy cảm trong phạm vi tính năng (mật khẩu, mã thẻ, thông tin liên lạc cá nhân, số dư công nợ).
- **Quy tắc che giấu và ghi log**: Tuyệt đối không ghi dữ liệu nhạy cảm dạng rõ vào application log, audit log thô hoặc payload của public Domain Events.

---

## 3. Detailed Business Rules and Logic

### Core Business Rules
1. **Rule 1**: <Quy tắc 1, ví dụ: Đơn hàng chỉ được xác nhận khi tổng giá trị > 0.>
2. **Rule 2**: <Quy tắc 2, ví dụ: Nếu sản phẩm thuộc diện quản lý serial (`isSerialTracked = true`), phiếu xuất kho bắt buộc phải nhập đủ mã serial tương ứng.>

### State Machine / Lifecycle
<Trình bày sơ đồ hoặc bảng chuyển trạng thái của thực thể chính.>

| From State | Trigger Event / Action | To State | Conditions & Invariants |
|---|---|---|---|
| `DRAFT` | Submit Order | `PENDING_CONFIRMATION` | Giỏ hàng có ít nhất một sản phẩm |
| `PENDING_CONFIRMATION` | Confirm Order | `CONFIRMED` | Tồn kho khả dụng đủ đáp ứng |

---

## 4. Data Model and Schema Impact

### Owned Entities (Internal Package)
<Mô tả các entity thuộc quyền sở hữu của module này.>
- `SampleEntity`: Lưu trữ thông tin ...
  - `id`: UUID (Primary Key)
  - `code`: String (Unique)
  - `status`: String

### Cross-Module References
- **Quy tắc**: Tuyệt đối không khai báo JPA Relationship (`@ManyToOne`, `@OneToMany`) sang entity của feature khác. Chỉ lưu ID thuần:
  - `customerId`: UUID (Tham chiếu sang module `crm`)
  - `orderId`: UUID (Tham chiếu sang module `sales`)

### Database Migration
- Bảng mới cần tạo: `<tên_bảng>`
- Chỉ mục (Indexes): `<tên_index>` trên các cột ...
- Khóa ngoại Database (Foreign Keys): <Quyết định FK vật lý ở mức DB nếu cần bảo đảm toàn vẹn schema.>

---

## 5. API and Contract Specifications

### 5.1. External HTTP Endpoints (Client Facing)

Mọi endpoint HTTP phải tuân thủ chuẩn `docs/contracts/api-conventions.md`.

- **Endpoint**: `POST /api/v1/<feature-endpoint>`
- **Operation ID / Purpose**: <Mô tả mục đích xử lý>
- **Required Authorization**: `ROLE_STAFF` hoặc quyền `feature:create`
- **Request Headers**: `Authorization: Bearer <token>`
- **Request Body**:
  ```json
  {
    "code": "STRING",
    "quantity": 1
  }
  ```
- **Validation Rules**: `@NotBlank`, `@Min(1)`
- **Success Response (HTTP 200/201)**: Gói trong `ApiResponse<T>`:
  ```json
  {
    "success": true,
    "code": "SUCCESS",
    "message": "Thao tác thành công",
    "data": {
      "id": "e2a1b3c4-1234-5678-9abc-def012345678"
    },
    "timestamp": "2026-10-16T15:30:00.000Z"
  }
  ```
- **Business Error Contract (`BaseErrorCode`)**:
  | HTTP Status | Error Code | Error Message | Condition |
  |---|---|---|---|
  | `400 BAD_REQUEST` | `SYS_003` | Dữ liệu đầu vào không hợp lệ | Vi phạm Bean Validation (`FieldErrorDetail`) |
  | `404 NOT_FOUND` | `SYS_004` | Không tìm thấy tài nguyên yêu cầu | ID không tồn tại |
  | `409 CONFLICT` | `SAMPLE_001` | Mã đã tồn tại trên hệ thống | Trùng mã nghiệp vụ duy nhất |

### 5.2. Internal Synchronous Java Interface (`features/<feature>/api`)

Đặc tả các phương thức Java API công khai theo chuẩn `docs/architecture/module-contracts.md`:

| Contract Attribute | Specification Value | Description |
|---|---|---|
| **Contract Interface & Method** | `<Feature>Api.<methodName>()` | Tên interface và method công khai |
| **Caller / Consumer** | `<CallerFeatureName>` | Tên module hoặc use case gọi vào |
| **Callee / Provider** | `<ThisFeatureName>` (Owning Module) | Module hiện thực hóa interface |
| **Input Parameters** | `<MethodRequestDto>` | DTO đầu vào, ghi rõ nullability và constraints |
| **Return Value** | `<MethodResponseDto>` | DTO đầu ra (tuyệt đối không dùng JPA Entity) |
| **Error Conditions** | `AppException` mang `<SpecificErrorCode>` | Danh sách lỗi nghiệp vụ có thể ném ra |
| **Authorization Context** | `CurrentUser` / `Required Permission` | Quyền hạn cần kiểm tra trước khi thực thi |
| **Transaction Behavior** | `REQUIRED` / `SUPPORTS` / Read-Only | Phạm vi transaction của phương thức |
| **Compatibility Strategy** | Backward compatible / Versioning | Kế hoạch tương thích khi mở rộng DTO |

### 5.3. Domain Events (`features/<feature>/api/event`)

Đặc tả các Domain Event (Java `record`) phát ra hoặc lắng nghe theo chuẩn `docs/architecture/module-contracts.md`:

| Event Attribute | Specification Value | Description |
|---|---|---|
| **Event Name** | `<Entity><Action>CompletedEvent` | Java Record đặt tên theo thể quá khứ |
| **Publisher** | `<ThisFeatureName>` | Module phát ra sự kiện |
| **Expected Consumers** | `<ConsumerFeatureA>`, `<ConsumerFeatureB>` | Các module đăng ký lắng nghe sự kiện |
| **Payload Attributes** | `UUID eventId`, `UUID aggregateId`, `Instant occurredAt` | Danh sách trường tối thiểu trong record |
| **Publish Timing** | Cuối service use case trước khi commit | Thời điểm sự kiện được đưa vào context |
| **Transaction Phase** | `TransactionPhase.AFTER_COMMIT` | Giai đoạn transaction kích hoạt listener |
| **Idempotency Key** | `eventId` hoặc `<aggregateId> + <status>` | Khóa để Consumer kiểm tra chống trùng lặp |
| **Retry & Replay Policy** | Max 3 retries, exponential backoff, Dead-Letter log | Chiến lược thử lại khi listener gặp lỗi |
| **Failure Observability** | Alert log `ERROR` kèm `eventId`, lưu bảng FailedEvents | Cách thức ghi nhận lỗi vận hành |
| **Pending Business State** | `PENDING_SYNC` / `PROCESSING` | Trạng thái của thực thể khi chờ consumer xử lý |

---

## 6. Concurrency, Transactions, and Failure Scenarios

### Concurrency Controls
- <Cơ chế khóa: Optimistic Lock (@Version), Pessimistic Lock, hoặc Database Unique Constraint để chống race-condition.>

### Transaction Boundaries
- <Quy định phạm vi transaction: Điểm bắt đầu và kết thúc commit của use case.>

### Failure and Recovery
- <Xử lý khi service bên ngoài hoặc event listener thất bại: Trạng thái chờ, Retry policy, Dead-letter log.>

---

## 7. Acceptance Criteria (AC)

- [ ] **AC-01**: <Tiêu chí 1: Người dùng nhập thông tin hợp lệ -> Hệ thống lưu thành công và trả về mã HTTP 200/201 kèm ApiResponse chuẩn.>
- [ ] **AC-02**: <Tiêu chí 2: Người dùng nhập dữ liệu không hợp lệ -> Hệ thống trả về HTTP 400 kèm danh sách FieldErrorDetail.>
- [ ] **AC-03**: <Tiêu chí 3: Người dùng không có quyền truy cập -> Hệ thống trả về HTTP 403 với mã AUTH_002.>
- [ ] **AC-04**: <Tiêu chí 4: Khi xử lý nghiệp vụ thành công -> Domain Event được phát ra với đầy đủ payload và listener thực thi an toàn idempotent.>

---

## 8. AC-to-Test Verification Matrix

| AC ID | Verification Method | Test Scope / Target | Expected Result | Pass/Fail Criteria |
|---|---|---|---|---|
| **AC-01** | Integration Test (`@SpringBootTest` / MockMvc) | Controller + Service + DB | HTTP 200/201, `success: true`, bản ghi được tạo trong DB | Đúng schema JSON và bản ghi DB tồn tại |
| **AC-02** | Unit / Controller Test | Controller Validation | HTTP 400, `code: SYS_003`, danh sách errors đầy đủ | Bắt đúng field lỗi trong FieldErrorDetail |
| **AC-03** | Security Test | Controller Security Filter | HTTP 403, `code: AUTH_002` | Chặn truy cập trái phép |
| **AC-04** | Domain Event Verification | Service Integration Test | TestEventListener bắt được event sau commit, xử lý idempotent | Payload chính xác, retry an toàn |
