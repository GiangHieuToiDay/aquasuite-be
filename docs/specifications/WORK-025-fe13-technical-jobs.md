# Risk-L Feature Specification — FE-13 Công việc kỹ thuật (UC-59 / UC-60 / UC-61)

## Metadata

- **Work ID**: `WORK-025`
- **Feature / Use Case**: FE-13 Installation and Repair Job Management — UC-59 Look up Technical Jobs and Schedule, UC-60 Accept and Execute Technical Jobs, UC-61 Record Results and Complete Technical Jobs
- **Version**: SRS v1.4 (doc `1guWQuWFbDLmf32yyiwpBow_JSDFwfUag_Ij1_tJhjp8`)
- **Owner**: Pham Van Nam (NamPham14)
- **Owning Module**: `servicedesk`
- **Risk Reasons**: public contract (event mới `OrderConfirmedEvent`), cross-module (listen Sales), data (vòng đời trạng thái công việc)
- **Design Review**: PENDING
- **Decision Owner**: Product Owner / Tech Lead

## 1. Goal and Scope

### Problem and Desired Outcome

Module `servicedesk` hiện mới chỉ có 2 entity trơ (`TechnicalJob`, `WarrantyRecord`), **chưa có** bất kỳ API/nghiệp vụ nào. Nhân viên kỹ thuật (Technical Staff) **chưa có** cách tra cứu lịch công việc được phân công, tiếp nhận/từ chối việc, và ghi nhận kết quả hoàn tất. Đồng thời đơn công trình (`PROJECT`) sau khi xác nhận **chưa** tự phát sinh công việc lắp đặt, buộc phải tạo tay.

Kết quả mong muốn: một luồng "Công việc kỹ thuật" phía Technical Staff gồm **tra cứu** (danh sách/lịch, chi tiết), **tiếp nhận & thực hiện** (accept/reject), **nghiệm thu & hoàn tất** (ghi kết quả → đóng việc); cùng cơ chế **tự sinh công việc lắp đặt** từ đơn dự án đã xác nhận — tuân thủ GB-40.

### In Scope

- **A. Tra cứu / lịch làm việc (UC-59)**: `GET /api/v1/technical-jobs` — phân trang, lọc `status`, `jobType`, khoảng lịch hẹn `from`/`to` (trên `scheduledAt`). Phạm vi dữ liệu tự áp: Technical Staff chỉ thấy việc của mình, Shop Owner thấy toàn bộ.
- **B. Chi tiết công việc (UC-59)**: `GET /api/v1/technical-jobs/{id}`.
- **C. Tiếp nhận / từ chối (UC-60)**: `POST /{id}/accept` (ASSIGNED → IN_PROGRESS), `POST /{id}/reject` (ASSIGNED → REJECTED, kèm lý do).
- **D. Ghi kết quả & hoàn tất (UC-61)**: `POST /{id}/complete` (IN_PROGRESS → COMPLETED, ghi `result` + `technicalNote`, set `completedAt`).
- **E. Auto-sinh công việc lắp đặt từ Order (Giai đoạn 2a)**: đơn `PROJECT` khi chuyển `PROCESSING` phát `OrderConfirmedEvent`; `servicedesk` sinh `TechnicalJob` loại `INSTALLATION`, trạng thái `ASSIGNED`, chưa phân công.
- **F. Phân công công việc (Chủ cửa hàng)**: `POST /{id}/assign` — gán `technicianId` cho công việc đang `ASSIGNED` hoặc `REJECTED` (phân công lại). Khép luồng auto: việc auto-sinh chưa có người → Chủ phân công → kỹ thuật viên accept.
- **G. Tạo công việc thủ công (Chủ cửa hàng)** — Hướng A: `POST /technical-jobs` — tạo công việc cho **bất kỳ khách nào** (lẻ/quầy/công trình) và **cả 3 loại** (SURVEY/INSTALLATION/WARRANTY_REPAIR), không phụ thuộc loại đơn; có thể gán sẵn kỹ thuật viên. Giải quyết việc phân loại đủ 3 loại và cho phép đi lắp cả đơn bán lẻ, không chỉ đơn công trình.

### Out of Scope

- (Không còn) — việc tạo thủ công đã thuộc scope (mục G). Auto từ bảo hành vẫn ngoài scope (Giai đoạn 2b).
- **Auto-sinh công việc sửa chữa từ Bảo hành (Giai đoạn 2b)**: chờ dựng luồng *warranty request/claim* (hiện chỉ có `WarrantyRecord` = phạm vi bảo hành, chưa phải yêu cầu). Entity đã chừa `warrantyRecordId`.
- **Micro-state en-route/arrived, chữ ký số nghiệm thu, upload ảnh hiện trường/bảng ảnh**: đã chốt loại bỏ để nghiệp vụ gọn (chốt 2026-11-12). Có thể bổ sung ở phiên bản sau nếu cần.
- Thông báo (notification) cho kỹ thuật viên khi được phân công.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role/Permission | Data Restrictions |
|---|---|---|---|
| Technical Staff | Tra cứu (A,B), Tiếp nhận/Từ chối (C), Hoàn tất (D) | `TECHNICAL_STAFF` | **Chỉ công việc có `technicianId == current user`** (Restricted¹ theo Permission Matrix) |
| Shop Owner | Tra cứu (A,B), thao tác (C,D) toàn bộ, **Phân công (F)** | `SHOP_OWNER` | Toàn bộ công việc (Full) |

- Ràng buộc "chỉ việc của mình" enforce ở **tầng service** (`loadAndAuthorize`, và scope `technicianId` khi tra cứu), không chỉ dựa vào `@PreAuthorize`.
- Auto-sinh (E) là tiến trình hệ thống chạy sau commit đơn hàng; `createdBy` ghi theo `salesStaffId` của đơn.
- Không log dữ liệu nhạy cảm; không có secret.

## 3. Business Rules

1. **BR-01 (GB-40)**: Công việc lắp đặt/sửa chữa được giao cho Technical Staff, người ghi nhận trạng thái, ghi chú kỹ thuật, kết quả và hoàn tất.
2. **BR-02 (ownership)**: Technical Staff chỉ xem và thao tác công việc được phân cho chính mình. Shop Owner xem/thao tác toàn bộ.
3. **BR-03 (loại công việc)**: `jobType ∈ {SURVEY, INSTALLATION, WARRANTY_REPAIR}`.
4. **BR-04 (reject bắt buộc lý do)**: Từ chối phải có `reason` khác rỗng.
5. **BR-05 (complete bắt buộc kết quả)**: Hoàn tất phải có `result` khác rỗng; set `completedAt = now`.
6. **BR-06 (auto từ Order)**: Chỉ đơn `OrderType.PROJECT` khi đạt `OrderStatus.PROCESSING` mới sinh công việc `INSTALLATION`. Đơn `RETAIL` không sinh. Đơn `DRAFT` (chờ duyệt hạn mức) chưa sinh; chỉ sinh khi thực sự `PROCESSING`.
7. **BR-07 (idempotent auto)**: Một đơn chỉ sinh tối đa **một** công việc `INSTALLATION` (khóa theo `(orderId, INSTALLATION)`).
8. **BR-08 (phân công)**: Chỉ Chủ cửa hàng phân công. `technicianId` phải là người có vai trò `TECHNICAL_STAFF`. Chỉ phân công được việc đang `ASSIGNED` hoặc `REJECTED`. Phân công lại việc `REJECTED` sẽ đưa về `ASSIGNED` và xóa `rejectionReason` cũ.

State transition (vòng đời `TechnicalJob.status`):

| From | Action | To | Conditions/Invariants |
|---|---|---|---|
| (new) | Tạo/auto-sinh | `ASSIGNED` | Mặc định khi khởi tạo; `technicianId` có thể null |
| `ASSIGNED` | assign (F) | `ASSIGNED` | Chủ cửa hàng; set/đổi `technicianId` hợp lệ |
| `REJECTED` | assign lại (F) | `ASSIGNED` | Chủ cửa hàng; set `technicianId` mới, xóa `rejectionReason` |
| `ASSIGNED` | accept (UC-60) | `IN_PROGRESS` | Người thao tác sở hữu việc (hoặc Shop Owner) |
| `ASSIGNED` | reject (UC-60) | `REJECTED` | Bắt buộc `reason` |
| `IN_PROGRESS` | complete (UC-61) | `COMPLETED` | Bắt buộc `result`; set `completedAt` |
| `COMPLETED` | — | — | Trạng thái kết thúc, không chuyển tiếp |

## 4. Data and Migration Decisions

- **Owned entity/table**: `technical_jobs` (đã tồn tại). Thay đổi: `status` và `job_type` chuyển từ `String` sang enum (`@Enumerated(STRING)`, giữ nguyên cột `VARCHAR(32)`); default `status = ASSIGNED` (trước là `"RECEIVED"`).
- **KHÔNG thêm bảng/cột mới** (bỏ bảng ảnh, bỏ cột chữ ký). Các trường sẵn có đủ dùng: `result`, `technical_note`, `rejection_reason`, `completed_at`, `scheduled_at`.
- **Cross-module reference bằng scalar ID**: `customer_id`, `order_id`, `warranty_record_id`, `technician_id`, `created_by` — không JPA relation chéo module.
- **Migration**: không có dữ liệu production cho `technical_jobs` → đổi default/kiểu enum là additive, không cần backfill. Giá trị hợp lệ mới: `ASSIGNED | IN_PROGRESS | COMPLETED | REJECTED`.
- **Index**: tái dùng index sẵn có (`technician_id`, `order_id`, `status`, `job_type`, `scheduled_at`) phục vụ lọc UC-59.

## 5. Contracts

### HTTP API (`/api/v1/technical-jobs`)

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| `GET /` | `SHOP_OWNER` hoặc `TECHNICAL_STAFF` | query: `status?`, `jobType?`, `from?`, `to?`, paging (`scheduledAt ASC`) | `200` `Page<TechnicalJobListItemResponse>` | — |
| `POST /` | **`SHOP_OWNER`** | body `{jobType, customerId, serviceAddress, issueDescription, scheduledAt?, technicianId?, orderId?, warrantyRecordId?}` | `200` detail (ASSIGNED) | `SVD_006/008` |
| `GET /{id}` | `SHOP_OWNER` hoặc `TECHNICAL_STAFF` | path `id` | `200` detail | `SVD_001`, `SVD_002` |
| `POST /{id}/assign` | **`SHOP_OWNER`** | body `{technicianId (@NotNull), scheduledAt?}` — đặt/cập nhật lịch hẹn luôn khi phân công | `200` detail (ASSIGNED, có technician) | `SVD_001/006/007` |
| `POST /{id}/accept` | như trên | — | `200` detail (IN_PROGRESS) | `SVD_001/002/003` |
| `POST /{id}/reject` | như trên | body `{reason}` (`@NotBlank`) | `200` detail (REJECTED) | `SVD_001/002/003/004` |
| `POST /{id}/complete` | như trên | body `{result (@NotBlank), technicalNote?}` | `200` detail (COMPLETED) | `SVD_001/002/003/005` |

Mã lỗi (`ServicedeskErrorCode`): `SVD_001` not found (404), `SVD_002` not owned (403), `SVD_003` invalid transition (409), `SVD_004` reject reason required (400), `SVD_005` result required (400), `SVD_006` invalid technician / không phải TECHNICAL_STAFF (400), `SVD_007` job not assignable — sai trạng thái (409), `SVD_008` customer not found (400).

### Domain Event

| Event | Publisher/Consumers | Payload | Timing | Idempotency/Failure Handling |
|---|---|---|---|---|
| `sales.api.event.OrderConfirmedEvent` | Publisher: Sales (`SalesOrderServiceImpl` convert báo giá; `OrderCreditApprovalEventListener` DRAFT→PROCESSING). Consumer: Servicedesk (`OrderInstallationJobListener`) | `eventId, orderId, orderCode, customerId, orderType, salesStaffId, deliveryAddress, projectName, occurredAt` | Phát trong transaction tạo/duyệt đơn, khi đơn đạt `PROCESSING` | Consumer: `@TransactionalEventListener(AFTER_COMMIT)` + `@Transactional(REQUIRES_NEW)`; chỉ xử lý `PROJECT`; idempotent theo `(orderId, INSTALLATION)`; lỗi sinh job được log, không rollback đơn |

### Inter-Module Java API

Not applicable (không thêm synchronous Java API; giao tiếp Sales→Servicedesk hoàn toàn qua domain event).

## 6. Concurrency, Transaction and Failure Safety

- **Transaction boundary**: mỗi thao tác UC-59/60/61 là một transaction service (`@Transactional`, tra cứu `readOnly`). Chuyển trạng thái đọc-sửa-lưu trong cùng transaction.
- **Auto-sinh tách rủi ro**: chạy sau khi đơn hàng commit (`AFTER_COMMIT`) trong transaction riêng (`REQUIRES_NEW`) → lỗi sinh công việc không làm hỏng việc tạo/duyệt đơn.
- **Idempotency**: kiểm tra `existsByOrderIdAndJobType(orderId, INSTALLATION)` trước khi tạo; event phát lại không sinh trùng.
- **Lọc động an toàn trên Postgres**: tra cứu UC-59 dùng **JPA Specification** (chỉ thêm điều kiện khi có giá trị) thay vì `:param IS NULL` — tránh lỗi *"could not determine data type of parameter"* với tham số null kiểu timestamp/enum/uuid.
- **Partial failure**: nếu đơn commit nhưng sinh job lỗi → log error; có thể sinh lại thủ công (idempotent bảo vệ không trùng).

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Technical Staff tra cứu chỉ thấy việc của mình; lọc status/jobType/khoảng lịch đúng | Integration (`TechnicalJobSearchPostgresTest`) | PASS |
| AC-02 | accept: ASSIGNED→IN_PROGRESS; accept khi không ở ASSIGNED → lỗi transition | Unit (`TechnicalJobServiceTest`) | PASS |
| AC-03 | complete: IN_PROGRESS→COMPLETED, set result + completedAt; thiếu result → lỗi | Unit (`TechnicalJobServiceTest`) | PASS |
| AC-04 | Thao tác việc không thuộc mình → `SVD_002` (not owned) | Unit (`TechnicalJobServiceTest`) | PASS |
| AC-05 | Đơn PROJECT xác nhận → auto sinh 1 job INSTALLATION/ASSIGNED gắn orderId; RETAIL không sinh; không sinh trùng; thiếu salesStaffId bỏ qua | Unit (`OrderInstallationJobListenerTest`) | PASS |
| AC-06 | Chủ gán technician hợp lệ cho việc ASSIGNED; gán lại việc REJECTED → về ASSIGNED, xóa lý do; gán người không phải kỹ thuật viên → SVD_006; gán việc IN_PROGRESS → SVD_007 | Unit (`TechnicalJobServiceTest`) | PASS |

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| Gộp "tiếp nhận" và "thực hiện" (accept = IN_PROGRESS, không tách ACCEPTED) | Product Owner | implementation | **RESOLVED** (chốt 2026-11-12): gộp, state machine 4 trạng thái |
| Nguồn tạo công việc: tay vs auto | Product Owner | implementation | **RESOLVED**: Giai đoạn 1 làm thao tác technician trước; auto từ Order làm tiếp (2a); warranty để sau (2b) |
| Loại bỏ chữ ký số / ảnh / en-route-arrived | Product Owner | implementation | **RESOLVED** (chốt 2026-11-12): loại bỏ cho gọn |
| Điều kiện đơn sinh job lắp đặt | Product Owner | implementation | **RESOLVED**: `OrderType.PROJECT` khi đạt `PROCESSING` |
| Luồng warranty request → auto WARRANTY_REPAIR | Product Owner / Tech Lead | release Giai đoạn 2b | **OPEN**: chờ dựng warranty request/claim; chưa triển khai |
| Phân công công việc bởi Chủ cửa hàng | Product Owner | implementation | **RESOLVED**: đã làm `POST /{id}/assign` (mục F) — khép luồng auto |
| Dev-seeder để demo end-to-end | Tech Lead | manual demo | **OPEN**: chưa có; hiện kiểm chứng qua test |

## 9. Work Breakdown & Status (theo dõi)

Giai đoạn 1 — thao tác phía Technical Staff (UC-59/60/61): **DONE**

- [x] Enum `TechnicalJobStatus` (+ `canTransitionTo`), `TechnicalJobType` — `internal/enums/`
- [x] Entity `TechnicalJob`: status/jobType → enum, default `ASSIGNED` — `internal/entity/TechnicalJob.java`
- [x] `ServicedeskErrorCode` (SVD_001–005) — `internal/error/`
- [x] Repository `TechnicalJobRepository` (+ `JpaSpecificationExecutor`, `existsByOrderIdAndJobType`) + `TechnicalJobSpecifications` — `internal/repository/`
- [x] Payloads: `RejectTechnicalJobRequest`, `CompleteTechnicalJobRequest`, `TechnicalJobListItemResponse`, `TechnicalJobDetailResponse` — `internal/payload/`
- [x] Service `TechnicalJobService` + `impl/TechnicalJobServiceImpl` (ownership + transition) — `internal/service/`
- [x] Controller `TechnicalJobController` (`/api/v1/technical-jobs`) — `internal/controller/`
- [x] Test: `TechnicalJobSearchPostgresTest`, `TechnicalJobServiceTest`

Phân công của Chủ cửa hàng (khép luồng): **DONE**

- [x] `AssignTechnicalJobRequest` + `POST /{id}/assign` (`SHOP_OWNER`) + `assignJob` (validate role TECHNICAL_STAFF, trạng thái assignable, reassign REJECTED) + mã lỗi SVD_006/007
- [x] Test gán/gán-lại/role-sai/trạng-thái-sai trong `TechnicalJobServiceTest`

Tạo công việc thủ công (Hướng A): **DONE**

- [x] BE: `CreateTechnicalJobRequest` + `POST /technical-jobs` (`SHOP_OWNER`) + `createJob` (validate khách qua `CrmApi`, technician nếu gán sẵn) + mã lỗi SVD_008; test createJob success/customer-not-found
- [x] FE: `CreateTechnicalJobDialog` (chọn loại/khách/địa chỉ/mô tả/lịch/KTV), nút "Tạo công việc" (owner), hook dùng chung `useTechnicians`, customer từ `sales/customerApi.getCustomerOptions`

Giai đoạn 2a — auto sinh job lắp đặt từ Order: **DONE**

- [x] Event `OrderConfirmedEvent` — `sales/api/event/`
- [x] Publish khi PROJECT→PROCESSING: `SalesOrderServiceImpl` (convert báo giá) + `OrderCreditApprovalEventListener` (DRAFT→PROCESSING)
- [x] Listener `OrderInstallationJobListener` (AFTER_COMMIT, REQUIRES_NEW, idempotent) — `servicedesk/internal/listener/`
- [x] Test: `OrderInstallationJobListenerTest` + cập nhật `SalesOrderPostmanSimulationTest` (constructor)

Giai đoạn 2b — auto sinh job sửa chữa từ Bảo hành: **TODO (chờ luồng warranty request)**

- [ ] Dựng warranty request/claim entity + luồng tiếp nhận
- [ ] Event bảo hành → listener sinh `WARRANTY_REPAIR` (điền `warrantyRecordId`)

Việc nền hỗ trợ demo: **DONE**

- [x] Dev-seeder `TechnicalJobDevDataInitializer` (profile `dev`, chỉ khi bảng rỗng): tạo 3 job mẫu (INSTALLATION/ASSIGNED chưa phân công, SURVEY/ASSIGNED gán technician, INSTALLATION/IN_PROGRESS gán technician) — resolve user qua `IamApi.findByEmail` (technical@/owner@/customer@aquasuite.local)

Trạng thái build: `mvn compile` PASS; unit + integration test của feature PASS. Chưa commit (theo AGENTS.md — commit/branch do người quyết định).
