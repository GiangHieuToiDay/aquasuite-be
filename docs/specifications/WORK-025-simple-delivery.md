# WORK-025 — Phân công và xác nhận giao hàng đơn giản

## Metadata

- **Work ID**: WORK-025
- **Feature / Use Case**: FE-08; lát triển khai UC40–43 trên phiếu đã xuất kho.
- **Version**: 1.0 — 2026-11-12.
- **Owner**: người phụ trách fulfillment; chưa xác định cá nhân.
- **Owning Module**: `fulfillment`.
- **Risk Reasons**: authorization, public HTTP contract, concurrency, additive migration, ảnh giao hàng.
- **Design Review**: Implementation authorized by the owner on 2026-11-12 after spec approval. Technical self-review recorded in the Brief; independent release review is still pending.
- **Decision Owner**: Product Owner chốt nghiệp vụ; Tech Lead/chủ module chốt quyền chỉnh entity và technical review.

## 1. Goal and Scope

### Problem and Desired Outcome

Kế toán phân công một hoặc nhiều phiếu/đợt giao cho shipper. Shipper xem việc của mình,
nhận hàng đi giao, cập nhật thành công kèm ảnh hoặc thất bại kèm lý do. Kế toán tra cứu
trạng thái và ảnh ngay trên đợt giao của đơn. Không quản lý chuyến, điểm dừng, GPS hoặc vị trí.

### In Scope

- Tái sử dụng `Delivery` và `DeliveryItem`, mỗi Delivery là một phiếu xuất kiêm đợt giao của một Order.
- Phân công, đổi/thu hồi phân công trước khi nhận hàng; danh sách kế toán và shipper.
- Xác nhận nhận hàng đi giao; thành công có ít nhất một ảnh, thất bại có lý do trong `note`.
- Chi tiết theo delivery và danh sách các đợt theo order; ảnh có kiểm tra quyền.
- Điểm tích hợp lưu ảnh, mặc định chưa cấu hình và báo lỗi rõ; không giả lập upload thành công.
- Tài liệu contract, test và migration bổ sung; chưa viết implementation trong đợt lập spec này.

### Out of Scope

- UC39 tạo/xác nhận phiếu xuất, trừ tồn, tiêu thụ/phục hồi reservation, nhập hoàn/cách ly hàng.
- Thu/đối soát COD, sửa paymentStatus; tự cập nhật trạng thái hoặc số lượng toàn đơn Sales.
- Giao một phần trong cùng Delivery; trong lát này thành công nghĩa là bàn giao đủ DeliveryItem.
- Quản lý lịch sử từng lần giao lại, thay shipper sau nhận hàng, nhận hàng trả lại hoặc hủy phiếu đã xuất.
- Chữ ký điện tử, kiểm tra chữ ký trong ảnh, tên người nhận thực tế, kế toán duyệt ảnh.
- Cloudinary adapter thật, SMS, bảo hành, trip/stop/vehicle/route/location/GPS và frontend.
- Không chứng nhận hoàn thành toàn bộ FE-08 hoặc hành trình end-to-end từ bán hàng đến kho/tài chính.

### Nguồn và quyết định thay thế

- Quyết định người dùng trong phiên ngày 12/11/2026 thay phạm vi BA rộng hơn: kế toán phân công,
  không trip/stop/GPS; bắt buộc ảnh nhưng không kiểm tra chữ ký; dùng `note` thay failureReason;
  Cloudinary để sẵn; ưu tiên dùng lại, không sửa code người khác.
- BA tham chiếu: https://github.com/GiangHieuToiDay/aqs-ba/blob/main/usecase-description/FE-08_Warehouse_Issue_And_Delivery.md
- SRS v1.4 ngày 11/11/2026: GB18–20, GB30–34; database PDF: deliveries/delivery_items.
- Running code trong checkout mới là bằng chứng hiện trạng. Artifact local của task khác không
  chứng minh implementation hiện có; không sửa các artifact đó để khớp checkout này.

## 2. Actors and Security

| Actor | Allowed Operations | Role | Data Restrictions |
|---|---|---|---|
| Kế toán | Tìm kiếm, chi tiết, xem ảnh, phân công/đổi/thu hồi | ACCOUNTANT | Trong hệ thống một cửa hàng; không cập nhật kết quả thay shipper |
| Shipper | Danh sách của mình, chi tiết/ảnh, nhận hàng, thành công/thất bại | SHIPPER | Chỉ Delivery có shipperId trùng actor đăng nhập |

- Dùng JWT và method security hiện có, không sửa IAM security config. Lấy actor từ `IamFacade`.
- Kiểm tra quyền trên bản ghi cho mọi detail/action/photo; không tin shipperId hoặc URL ảnh client gửi.
- Shipper chỉ đọc ảnh thuộc delivery hiện được giao cho mình. Sau hoàn thành vẫn được đọc nếu còn gán.
- Không log nội dung ảnh/token/credential; ảnh và thông tin người nhận không công khai.
- Lịch sử nghiệp vụ riêng không được tạo thêm bảng. Audit tập trung của thao tác nhạy cảm vẫn
  là dependency với chủ module audit, không được tuyên bố đã đáp ứng chỉ bằng updatedAt.

## 3. Business Rules

1. Chỉ phiếu đã xác nhận xuất theo contract UC39 được phân công. Dùng DISPATCHED theo nghĩa
   đã xuất kho; nguồn UC39 bảo đảm tồn/reservation đã xử lý, service giao hàng không thực hiện lại.
2. Một shipper nhận nhiều Delivery độc lập; không cần đối tượng chuyến. Một Order có nhiều batchNo.
3. Xác minh user đích tồn tại, ACTIVE và role SHIPPER qua public IamApi, không truy vấn users trực tiếp.
4. Phân công danh sách không rỗng, không trùng ID; giới hạn đề xuất 100 phiếu/lần. Cả danh sách atomic.
5. Chỉ đổi/thu hồi khi ASSIGNED, chưa startedAt. Không chỉnh orderId, items, warehouse, recipient hoặc COD.
6. Hoàn thành bắt buộc >=1 ảnh thực đã lưu. Chỉ xác minh định dạng/dung lượng/lưu thành công;
   không nhận diện chữ ký, không chữ ký riêng, không bước duyệt ảnh.
7. Giao thất bại yêu cầu reason không trắng, tối đa 1000 ký tự. Nối vào note hiện có cùng thời gian/actor,
   không xóa ghi chú cũ. Không có failureReason/failedAt mới.
8. Giao thất bại không tự cộng tồn. FAILED là kết quả cuối của lát này; chưa expose API reset/retry.
   Nhiều đợt giao không đồng nghĩa nhiều lần thử: mỗi đợt là một Delivery, không tạo DeliveryAttempt.
9. DELIVERED không suy ra toàn Order đã giao xong hoặc PAID. Không ghi transactions/receipts.
10. Không cho complete/fail thay đổi một kết quả cuối khác. API không có PATCH status tùy ý.
11. updatedAt là lần cập nhật gần nhất, không được quảng bá là lịch sử bất biến của từng thao tác.

| From | Action | To | Conditions/Invariants |
|---|---|---|---|
| DISPATCHED | assign | ASSIGNED | ACCOUNTANT; phiếu đã xuất; shipper hợp lệ |
| ASSIGNED | assign/reassign | ASSIGNED | Chưa startedAt; đổi shipper và assignedBy/At |
| ASSIGNED | unassign | DISPATCHED | Chưa startedAt; xóa shipperId/assignedBy/assignedAt |
| ASSIGNED | start | IN_TRANSIT | Đúng shipper; ghi startedAt một lần |
| IN_TRANSIT | complete | DELIVERED | Đúng shipper; ảnh thật lưu thành công; ghi deliveredAt |
| IN_TRANSIT | fail | FAILED | Đúng shipper; reason; nối note, không đổi tồn |

DRAFT/CANCELLED do quy trình khác sở hữu, ở đây chỉ đọc. Legacy IN_TRANSIT/DELIVERED có thể
thiếu metadata mới; không suy diễn ảnh đã tồn tại, không rewrite chúng khi migration.

## 4. Data and Migration Decisions

### Entity dùng lại

- `fulfillment.internal.entity.Delivery`: mọi trường sẵn có được giữ; bổ sung đúng bốn trường:

| Java field | PostgreSQL column | Null/default | Meaning |
|---|---|---|---|
| assignedBy: UUID | assigned_by UUID | NULL | Actor phân công gần nhất |
| assignedAt: OffsetDateTime | assigned_at TIMESTAMPTZ | NULL | Thời điểm phân công gần nhất |
| startedAt: OffsetDateTime | started_at TIMESTAMPTZ | NULL | Thời điểm nhận hàng đi giao |
| proofPhotos: List of photo metadata | proof_photos JSONB | NOT NULL DEFAULT '[]' | Metadata ảnh do backend tạo |

- `DeliveryItem`: dùng nguyên trạng, chỉ đọc trong lát này.
- Mỗi photo metadata gồm `photoId` UUID, `storageKey`, `mimeType`, `size`. Không lưu bytes/base64,
  chữ ký riêng, URL do client khai hoặc credential. DTO ra ngoài chỉ expose photoId và API đọc ảnh.
- Dùng lại `note`, `shipperId`, `deliveryStatus`, `deliveredAt`, scheduledDate và thông tin người nhận.
- Không thêm DeliveryActivity/Proof/Attempt, không bảng mới, không thêm version column.
- Cross-module chỉ scalar ID; không JPA relationship tới User/Order/Inventory.

### Migration và ownership

- Migration additive mới, chọn version trống tại lúc triển khai, không sửa baseline/migration đã có.
- Existing rows: metadata nullable; proof_photos = []; không backfill actor/giờ/ảnh giả.
- CHECK jsonb_typeof(proof_photos) = 'array'; ràng buộc complete có ảnh ở service để không làm hỏng
  các Delivery DELIVERED legacy không có ảnh. Không đổi trạng thái/dữ liệu cũ.
- Dùng index hiện có cho shipper/order/status; thêm composite index chỉ khi truy vấn thật cần.
- Quyền sở hữu: không sửa mã người khác. Phần thêm field vào Delivery và mapping là một hạng mục
  handoff cho chủ file, trừ khi người dùng giao rõ quyền sửa. Spec này không ngầm cấp quyền đó.
- Rollback deployment: giữ cột/ảnh, không tự drop dữ liệu. Không chạy migration lên DB local/live trong task tài liệu.

## 5. Contracts

### HTTP API

Base `/api/v1/fulfillment`; tất cả response nghiệp vụ bọc ApiResponse. Photos trả bytes với MIME
đã xác minh và `X-Content-Type-Options: nosniff`. Danh sách mặc định size 20, tối đa 100, sort ổn định.

| Method/path dưới base | Authorization | Request/validation | Success | Errors |
|---|---|---|---|---|
| GET /deliveries | ACCOUNTANT | status, shipperId, scheduledFrom/To, deliveryCode, orderId, pageable | 200 page summaries | 400 filter sai |
| GET /deliveries/{id} | ACCOUNTANT hoặc shipper được gán | UUID | 200 detail + items + photo IDs | 403/404 |
| GET /orders/{orderId}/deliveries | ACCOUNTANT | UUID; page | 200 page, rỗng nếu không có delivery; không chứng minh order tồn tại | 400 |
| PUT /delivery-assignments | ACCOUNTANT | shipperId; deliveryIds 1..100, unique | 200 updated summaries | 400,403,404,409 |
| DELETE /deliveries/{id}/assignment | ACCOUNTANT | Chỉ trước nhận hàng | 200 success | 403,404,409 |
| GET /me/deliveries | SHIPPER | status, ngày hẹn, pageable; không nhận shipperId | 200 page của actor | 400 |
| POST /deliveries/{id}/start | SHIPPER được gán | Không body | 200 detail | 403,404,409 |
| POST /deliveries/{id}/complete | SHIPPER được gán | multipart photos, 1..5 file, <=5 MiB/file; JPEG/PNG/WebP | 200 detail | 400/413/415,403,404,409,503 |
| POST /deliveries/{id}/fail | SHIPPER được gán | JSON reason 1..1000 ký tự sau trim | 200 detail | 400,403,404,409 |
| GET /deliveries/{id}/photos/{photoId} | ACCOUNTANT hoặc shipper được gán | Photo phải thuộc delivery | 200 image bytes | 403,404,503 |

- Request assign: `{ "shipperId": "UUID", "deliveryIds": ["UUID"] }`.
- Request fail: `{ "reason": "Không liên hệ được người nhận" }`.
- Detail gồm metadata Delivery, trạng thái, thông tin shipper qua IAM, items qua DeliveryItem,
  và photoId/read endpoint; không trả entity/storageKey ra HTTP.
- Không tìm theo orderCode bằng join bảng sales; hiện hỗ trợ orderId/deliveryCode để giữ boundary.
- Lỗi dùng CommonErrorCode khi phù hợp; FulfillmentErrorCode cho INVALID_STATE, INVALID_SHIPPER,
  INVALID_PHOTO, STORAGE_UNAVAILABLE. Không tự sửa GlobalExceptionHandler; mapping HTTP cần kiểm tra
  với handler hiện có, lỗi multipart quá giới hạn được kiểm chứng trong technical review.

### Component tối thiểu

| Component | Nội dung |
|---|---|
| DeliveryRepository | Search page, findByOrder, findByShipper, lock ID(s) để ghi |
| DeliveryItemRepository | Read theo một/nhiều deliveryId |
| DeliveryService | search/detail/byOrder/myDeliveries/assign/unassign/start/complete/fail/readPhoto |
| DeliveryPhotoStorage | store/read; adapter chưa cấu hình fail rõ; không URL giả |
| DeliveryController | Kế toán quản lý, shared detail/photo có object authorization |
| ShipperDeliveryController | me list, start/complete/fail; dùng chung DeliveryService |
| Payload/error/enum | Request records, summary/detail/item/photo responses; DeliveryStatus; FulfillmentErrorCode |

Không tạo interface + implementation service/mapper thừa nếu không có nhu cầu; chỉ tách MapStruct
khi mapping thực sự cần và không chứa business logic. REST payload đặt internal/payload.

### Inter-Module Java API

Không thêm public Java API nghiệp vụ trong lát này. Dùng public contracts có sẵn:

| Provider | Consumer/method | Transaction | Compatibility/errors |
|---|---|---|---|
| iam | fulfillment: IamFacade.getCurrentUserId; IamApi.getUserById | Actor hiện tại; lookup chỉ đọc | Không sửa contract; thiếu/không hợp lệ actor/shipper thì từ chối |
| catalog | fulfillment: CatalogApi.getSkuSummaries | Read-only, phục vụ hiển thị | Không ghi catalog; không thay snapshot bằng giá live |

IAM shipper picker: dùng HTTP user-list hiện có nếu lọc role/status được; nếu chưa có, chủ IAM
cung cấp contract. Không tự tạo fulfillment endpoint để query users hoặc giả danh sách shipper.
Finance OrderReceivableApi chỉ dùng nếu cần bổ sung read view, không bắt buộc cho lát cơ bản.
Không dùng crm.internal Cloudinary/avatar service hoặc finance.internal.

### Domain Events và dependency ngoài phạm vi

Chưa thêm event để kích hoạt sales/finance/warranty; không giả định consumer đã có.
Audit tập trung cần chủ audit thống nhất contract/consumer trước nghiệm thu release có audit.
Việc đó không cho phép tự chỉnh module audit. UC39 đầu vào và Sales aggregation có owner riêng.

## 6. Concurrency, Transaction and Failure Safety

- Khóa pessimistic Delivery khi ghi; bulk khóa theo UUID tăng dần để tránh deadlock. Kiểm tra quyền
  và state trong transaction sau khóa. Rollback toàn bộ bulk khi một phiếu sai.
- Assign cùng shipper/state đã ASSIGNED là no-op, không thay assignedAt. start đã IN_TRANSIT là no-op
  cho đúng actor. unassign đã DISPATCHED và không shipper là no-op. fail gửi lại cùng reason không nối
  note lần nữa; khác reason sau FAILED bị conflict. Không sửa trạng thái cuối bằng request cũ.
- complete đã DELIVERED bởi đúng shipper trả kết quả cũ, không upload/append ảnh hoặc phát tác động lại.
  Replay không được bypass object authorization; không cho thay bộ ảnh sau kết quả cuối.
- Upload ngoài DB transaction dài: precheck actor/state, lưu ảnh thật, sau đó khóa/recheck và commit
  metadata + DELIVERED. Nếu state đổi/DB rollback, xóa file mới hoặc đưa vào cơ chế dọn orphan.
  Complete đồng thời chỉ một bộ ảnh được tham chiếu; file thừa phải được dọn. Lifecycle storage cần
  cleanup capability nội bộ khi triển khai adapter thật, không thêm bảng ảnh.
- Content-type do client gửi chưa đủ: kiểm tra signature/khả năng decode ảnh, dung lượng và số file.
- Khi storage chưa cấu hình: complete trả STORAGE_UNAVAILABLE/503, giữ IN_TRANSIT, không lưu metadata
  giả/đánh dấu giao xong. Read ảnh thiếu cấu hình trả lỗi rõ. Không trả success bằng URL mock.
- Không dùng DB bytes tạm để lách Cloudinary dependency. Integration test dùng fake in-memory trong
  test scope; production không tự kích hoạt fake. Chưa có adapter thật thì chưa nghiệm thu E2E ảnh.

## 7. Acceptance Criteria and Verification

| AC | Observable outcome | Verification | Expected result |
|---|---|---|---|
| AC01 | Chỉ hai entity cũ; thêm bốn cột, dùng note | Diff/schema/boundary checks | Không bảng/field thừa hoặc sửa module khác |
| AC02 | Kế toán phân công nhiều phiếu atomic | PostgreSQL integration + security | Đúng shipper; một phiếu sai rollback cả batch |
| AC03 | Đổi/thu hồi trước nhận hàng | State/concurrency tests | Sau IN_TRANSIT bị chặn |
| AC04 | Shipper chỉ thấy và thao tác phiếu của mình | Controller security + service tests | Không truy cập ngang qua ID/ảnh |
| AC05 | Nhận hàng ghi startedAt một lần | Replay/concurrency tests | Không reset giờ khi gửi lại |
| AC06 | Complete bắt buộc ảnh, không kiểm tra chữ ký | Multipart/service tests | Thiếu/sai ảnh bị chặn; không cần trường signature |
| AC07 | Storage chưa có không giả success | Missing-adapter test | 503; không đổi state, không ảnh giả |
| AC08 | Complete lưu ảnh thật/metadata và deliveredAt khi có adapter | Test fake + future real-storage manual | Chính xác một kết quả; rollback dọn file |
| AC09 | Fail nối reason vào note | Tests | Giữ ghi chú cũ; replay không nối trùng; không cộng tồn |
| AC10 | Nhiều batch độc lập theo order | Query/integration tests | Xong một batch không đổi toàn order/payment |
| AC11 | Migration giữ legacy rows/ảnh trống | PostgreSQL migration test | Không backfill giả hoặc sửa baseline |
| AC12 | Không sửa code người khác | Diff review | Chỉ scope được giao; entity handoff tường minh |
| AC13 | Full suite và audit/dependency readiness được báo đúng | Review + verification logs | Không tuyên bố E2E/production khi còn dependency |

## 8. Open Decisions and Handoffs

| Decision/dependency | Owner | Required before | Status/resolution |
|---|---|---|---|
| Phạm vi đơn giản, ảnh bắt buộc nhưng không kiểm chữ ký, note dùng lại | Người dùng | Spec | CONFIRMED 2026-11-12 |
| Chỉnh Delivery hiện hữu và bốn field | Chủ file/Tech Lead | Compile/schema integration | Handoff; chưa cho phép tự sửa code người khác |
| Technical Design Review | Tech Lead | Production coding theo Risk L | PENDING; product confirmation không giả thành technical review |
| Cloudinary/storage thật | Chủ tích hợp | Complete E2E và release ảnh | DEFERRED theo người dùng; stub phải fail rõ |
| UC39 phiếu đã xuất thật | Chủ kho/fulfillment UC39 | E2E từ đơn bán | Dependency; không seed dữ liệu thật để giả luồng |
| Shipper picker nếu IAM list chưa đủ | Chủ IAM | FE chọn người đầy đủ | Reuse-first; không sửa IAM trong task này |
| Audit tập trung | Chủ audit | Release đáp ứng invariant audit | Chưa có write contract dùng lại trong checkout đã đọc |
| Retry sau FAILED/giao một phần | Product Owner | Mở rộng sau | OUT OF SCOPE; không âm thầm thêm Attempt |

Không cần hỏi lại nghiệp vụ đã chốt. Những handoff trên không ngăn hoàn thành tài liệu;
không tự đánh dấu implementation hoặc technical review đã hoàn tất.

## Implementation decisions - 2026-11-12

- Latest owner instruction authorizes this implementation, including the four Delivery fields
  within fulfillment. No source belonging to another module is changed. This supersedes the
  earlier Delivery handoff restriction for these four fields only.
- Use IamApi.getCurrentUser(), not IamFacade: only IamApi has a running bean in this checkout.
  This reuses the same public IAM identity/role contract without modifying IAM.
- Mutation responses are Summary; call detail for items/photos. Filter exact deliveryCode/orderId;
  scheduled dates use Asia/Ho_Chi_Minh. No direct Sales table query.
- IAM user-list is SHOP_OWNER-only; ACCOUNTANT shipper picker needs an IAM owner handoff.
- JPEG/PNG use JDK decoders; WebP requires a runtime ImageIO decoder. Reject unsupported decoders
  safely; do not change another module or pom to install one in this slice.
- Existing global multipart limit is 6MB/file and 7MB/request. Service permits 1..5 files at
  <=5 MiB each, but the request must also fit that existing global limit. No global config change.
- Cloudinary, central audit, and real PostgreSQL verification remain release dependencies.
- API guide: [WORK-025-simple-delivery-api.md](../contracts/WORK-025-simple-delivery-api.md).

## 2026-11-12 — Follow-up owner decision: temporary filesystem storage

Supersedes the deferred/unavailable-default storage implementation above: use a real local filesystem
adapter for now, keeping mandatory photos, authorized reads, and cleanup of unreferenced uploads.
Default directory: ./uploads/delivery-photos; configurable with aquasuite.delivery.photo-directory.
No trip/stop model: batch_no already represents batches of one order. Failed delivery does not return
stock automatically; warehouse management must confirm returned goods separately. Delivery completion
does not mark the whole order complete or paid; COD declaration and physical receipt remain separate.
