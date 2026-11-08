# WORK-020 — UC-32 Quản lý nhập kho theo đơn mua

## Metadata

- **Work ID**: WORK-020
- **Feature / Use Case**: FE-07 / UC-32 Manage Goods Receipt
- **Version**: 0.2 — DRAFT
- **Owner**: Chưa chỉ định
- **Owning Module**: inventory
- **Risk Reasons**: inventory, concurrency, public contract, cross-module, data migration, authorization
- **Design Review**: PENDING
- **Decision Owner**: Product Owner và Tech Lead; phân quyền do security owner xác nhận
- **Nguồn nghiệp vụ**: [SRS AquaSuite](https://docs.google.com/document/d/13fovzaSXAqCsBeH4IWyejGwdw1nOLY5nEsApRgapaFQ/edit) và quyết định của người dùng trong phiên làm việc. Tài liệu phân loại PO không thống nhất UC-27/UC-34; cần xác nhận mã, không ảnh hưởng tên UC-32.

### Quyết định đã chốt trong phiên làm việc (2026-11-08)

1. **Mô hình thao tác (Hướng B)**: Tạo phiếu và xác nhận là **một hành động atomic**. Không có trạng thái nháp. Khi xác nhận thành công, tồn được cộng ngay và phiếu trở nên bất biến. (Nếu sau này cần nháp sẽ bổ sung additive, không nằm trong phạm vi hiện tại.)
2. **Quyền thao tác UC-32**: Shop Owner và Accountant.
3. **Điều kiện nhận hàng**: PO phải ở `CONFIRMED` hoặc `PARTIALLY_RECEIVED` — khớp với contract `PurchasingApi.onGoodsReceiptConfirmed` hiện có; đây là điều kiện bắt buộc để cho phép giao bổ sung.
4. **Đóng đơn thiếu (short-close)**: Là nghiệp vụ của module purchasing (đã có, lỗi `PO_013`), **ngoài phạm vi UC-32**. UC-32 không tự chuyển PO sang trạng thái đóng.
5. **Sửa sai số liệu sau xác nhận (điều chỉnh tồn/ADJUSTMENT)**: **Ngoài phạm vi UC-32**, sẽ được triển khai sau khi UC-32 hoàn tất. UC-32 chỉ cam kết không khóa cứng khả năng này (không cấm tạo stock movement loại khác sau đó).

## 1. Goal and Scope

### Problem and Desired Outcome

Chủ cửa hàng kiểm hàng ngoài hệ thống trước khi lập phiếu. Hệ thống ghi nhận số SKU thực tế được chấp nhận theo đơn mua; khi phiếu được xác nhận, tồn kho và tiến độ đơn mua được cập nhật nhất quán và có thể truy vết.

### In Scope

- Tra cứu PO đủ điều kiện, số lượng đặt, đã nhận, còn lại và kho.
- Lập và xác nhận phiếu nhập theo PO trong **một hành động atomic**; hỗ trợ nhiều phiếu cho cùng một PO khi có giao bổ sung.
- Tra cứu danh sách, chi tiết và lịch sử phiếu nhập.
- Tăng tồn thực tế theo variant/kho, ghi stock movement và cập nhật tiến độ PO qua public contract của purchasing.
- Kiểm soát quyền, xử lý đồng thời, yêu cầu lặp và lỗi giao dịch.

### Out of Scope

- Lưu hàng giao dư, giao nhầm, ngoài đơn, hàng hỏng bị từ chối hoặc nguyên nhân từ chối; lập phiếu/biên bản cho đợt không nhận hàng.
- **Trạng thái nháp và quy trình sửa/hủy nháp** (đã chọn Hướng B — không nháp).
- **Sửa sai số liệu sau xác nhận / điều chỉnh tồn (ADJUSTMENT)**: sẽ triển khai sau UC-32; UC-32 chỉ không khóa cứng khả năng này.
- **Đóng đơn thiếu (short-close)**: thuộc purchasing, chỉ tham chiếu.
- Trả hàng sau nhập vì phát hiện lỗi chất lượng: cửa hàng tự chịu theo quyết định hiện tại.
- Thanh toán, công nợ, kế toán; tự tạo product/variant mới; tự gửi PO đến nhà cung cấp.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role/Permission | Data Restrictions |
|---|---|---|---|
| Shop Owner | Kiểm tra thực tế, tạo+xác nhận phiếu; xem danh sách/chi tiết/lịch sử | Ánh xạ permission cụ thể TBD theo IAM | Theo quyền kho nếu có |
| Accountant | Tạo+xác nhận phiếu; xem danh sách/chi tiết/lịch sử | Ánh xạ permission cụ thể TBD theo IAM | Theo quyền kho nếu có |
| Vai trò khác | Chỉ khi được phân quyền | TBD theo permission matrix và IAM hiện tại | Không tự giả định quyền |

- Bắt buộc xác thực và authorization ở backend; ghi người thực hiện và thời gian. Không ghi secret hoặc dữ liệu nhạy cảm không cần thiết vào log.
- Quyền thao tác đã chốt là Shop Owner và Accountant; tên permission cụ thể và giới hạn theo kho cần xác nhận trong design review theo IAM hiện tại.

## 3. Business Rules

1. Một PO dự kiến được giao một đợt; nếu còn thiếu, NCC giao bổ sung. Có thể lập **nhiều phiếu** cho một PO, mỗi phiếu tương ứng một lần thực tế có hàng được nhận.
2. Chủ cửa hàng/kế toán kiểm tra hàng **trước khi** lập phiếu. Chỉ số hàng đúng SKU thuộc đơn và được chấp nhận mới ghi trong phiếu và cộng tồn. Hàng dư, ngoài đơn và hỏng bị từ chối **không được lưu** trong UC này.
3. Nếu toàn bộ hàng không được nhận thì **không lập phiếu** và không đổi tồn/PO. Không lưu nguyên nhân.
4. **Mô hình Hướng B**: Tạo phiếu và xác nhận là một hành động atomic duy nhất. Không có nháp. Phiếu sau khi ghi nhận là **bất biến** — không sửa/hủy trực tiếp.
5. **Điều kiện nhận**: Chỉ nhận khi PO ở `CONFIRMED` hoặc `PARTIALLY_RECEIVED`. PO `DRAFT`, `COMPLETED`, `CANCELLED` hoặc đã đóng thiếu không nhận thêm.
6. **Validation mỗi dòng phiếu** (chạy ở backend, không tin riêng frontend):
   - SKU/variant phải thuộc `purchase_order_items` của chính PO đó.
   - Số nhận lớn hơn 0.
   - Số nhận không vượt `quantityOrdered − quantityReceived` tại thời điểm xác nhận.
   - Kiểu số/đơn vị/độ chính xác theo contract PO đã duyệt.
   - Bốn kiểm tra trên phải được kiểm chứng lại **bên trong transaction có khóa dòng PO** (xem Mục 6), không chỉ dựa trên số client gửi lên.
7. `quantityReceived` là tổng số lượng được chấp nhận đã xác nhận cộng dồn qua các phiếu; `remaining = quantityOrdered − quantityReceived`.
8. Chỉ xác nhận phiếu mới tăng on-hand theo variant và kho. Nếu chưa có dòng tồn thì tạo an toàn, nếu có thì cộng; ghi stock movement tham chiếu chứng từ để truy vết tới phiếu nhập.
9. Nhận một phần đưa PO sang `PARTIALLY_RECEIVED`; khi mọi dòng nhận đủ thì `COMPLETED`. Trường hợp NCC không giao tiếp dùng **đóng đơn thiếu của purchasing** (ngoài UC-32), có lý do; không đồng nhất với nhận đủ.
10. Phiếu đã xác nhận là bất biến. Sai số liệu sau xác nhận được xử lý bằng **cơ chế điều chỉnh tồn (ADJUSTMENT) ngoài UC-32**, triển khai sau, có người thực hiện/lý do/truy vết, **không sửa trực tiếp phiếu cũ và không trả hàng**.

### Lifecycle phiếu nhập (Hướng B — đã chốt)

| From | Action | To | Conditions/Invariants |
|---|---|---|---|
| Chưa có | Tạo + Xác nhận (một hành động atomic) | CONFIRMED | PO ở `CONFIRMED`/`PARTIALLY_RECEIVED`; mọi dòng qua validation Mục 3.6 dưới khóa; cộng tồn + ghi movement + cập nhật `quantityReceived` nhất quán. |
| CONFIRMED | Sửa/hủy trực tiếp | Không cho phép | Phiếu bất biến. Sửa sai dùng ADJUSTMENT ngoài UC-32. |

> Không có trạng thái DRAFT/CANCELLED trong phạm vi này.

## 4. Data and Migration Decisions

- Dự kiến dùng `goods_receipts`, `goods_receipt_items`, `inventories`, `stock_movements` thuộc inventory; liên kết `purchase_orders`, `purchase_order_items` của purchasing qua **scalar ID và public contract** (không JPA chéo module).
- SKU thuộc catalog; không tự tạo product. Tồn được phân biệt theo variant và kho; baseline có unique `(variant_id, warehouse_code)`.
- Cần kiểm tra đầy đủ schema/entity, loại số lượng, đơn vị, trạng thái, kho, audit và khả năng truy vết trước khi quyết định migration. Không giả định các bảng hiện có đã đủ.
- Migration dự kiến additive nếu cần; dữ liệu cũ, backfill, rollback/recovery và ảnh hưởng deployment phải được review trước khi phê duyệt.
- Unique và khóa phải ngăn trùng tồn, trùng xử lý phiếu và đảm bảo liên kết dòng phiếu/PO; thiết kế chi tiết TBD.

## 5. Contracts

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| TBD: tạo+xác nhận phiếu (một hành động), danh sách, chi tiết | Shop Owner / Accountant theo IAM | PO, kho, dòng PO và số nhận; áp dụng validation Mục 3.6 | `ApiResponse<T>` theo convention | Mã lỗi và HTTP status TBD: quyền, trạng thái PO, số lượng vượt/không hợp lệ, SKU ngoài đơn, xung đột đồng thời |

Không chốt URI, DTO hoặc mã lỗi trước khi đối chiếu code và design review.

### Inter-Module Java API — contract HIỆN CÓ (đã tồn tại trong code)

> Mâu thuẫn spec↔code đã được giải quyết: contract tích hợp purchasing **không còn là TBD**. Các phần sau phản ánh code thực tế.

| Provider | Consumer | Method/DTO hiện có | Ghi chú |
|---|---|---|---|
| purchasing | inventory | `PurchasingApi.onGoodsReceiptConfirmed(UUID purchaseOrderId, Map<UUID,BigDecimal> receivedItems)` | Cộng dồn `quantityReceived`; tự set `PARTIALLY_RECEIVED`/`COMPLETED`; **bỏ qua** nếu PO không ở `CONFIRMED`/`PARTIALLY_RECEIVED`. |
| purchasing | inventory | `PurchasingApi.getIncomingStockQty` / `getIncomingStockQtyMap` | Tra cứu số hàng sắp về theo variant từ PO đang mở. |

**Khoảng trống cần xử lý trong UC-32**: `onGoodsReceiptConfirmed` hiện **không tự kiểm tra nhận vượt số còn lại** — nó chỉ cộng `qtyReceivedNow` vào `quantityReceived`. Do đó phía **inventory/UC-32 phải validate "không vượt remaining" (Mục 3.6) TRƯỚC khi gọi** contract này, và phải đảm bảo tính atomic giữa cộng tồn (inventory) và cập nhật tiến độ (purchasing). Cách đảm bảo atomic/giao dịch chung qua hai module cần chốt trong design review.

- Không có domain event được quyết định; contract hiện tại là lời gọi đồng bộ. Nếu chuyển sang event phải đặc tả idempotency, retry/replay, observability, reconciliation trước khi duyệt.
- **Phát hiện vi phạm boundary (chưa xử lý trong phiên này)**: `PurchaseOrderServiceImpl`, `PurchasingDataInitializer` và test của purchasing đang import `com.aquasuite.ims.module.inventory.internal.*` (Supplier, SupplierProduct, repository), trái quy tắc #2 của AGENTS.md. Supplier hiện nằm trong inventory.internal trong khi PO thuộc purchasing. Cần owner quyết định hướng xử lý trước khi code phần liên quan; spec này không phê duyệt vi phạm.

## 6. Concurrency, Transaction and Failure Safety

- **Khóa dòng PO khi xác nhận**: đọc số còn lại → validate → cộng dồn `quantityReceived` → cộng tồn → ghi movement phải nằm gọn trong một transaction có khóa (`SELECT ... FOR UPDATE`/pessimistic, hoặc optimistic `@Version` + retry) trên dòng PO liên quan. Mục tiêu: hai phiếu của cùng PO xác nhận đồng thời không cùng đọc số cũ rồi cùng vượt số còn lại.
- Xác nhận cùng phiếu hai lần chỉ tạo một hiệu ứng (idempotent). Xác nhận đồng thời với hủy/đóng PO phải nhất quán (contract bỏ qua nếu PO không ở trạng thái mở).
- Cập nhật đồng thời cùng variant/kho, kể cả lần đầu tạo dòng tồn, không được mất cập nhật hoặc tạo trùng (unique `(variant_id, warehouse_code)` + upsert an toàn). Thứ tự khóa nhiều dòng cần tránh deadlock.
- Phiếu, `quantityReceived` của PO, tồn và movement cần atomic hoặc cơ chế nhất quán/khôi phục được owner phê duyệt. Nếu bất kỳ bước nào lỗi, không để cập nhật dở dang; nếu client timeout sau commit, retry phải trả kết quả phù hợp mà không cộng thêm.
- Lựa chọn lock/version, idempotency key và hành vi retry/conflict cụ thể quyết định trong design review.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Xem PO đủ điều kiện và tiến độ | Integration/contract | Đúng kho, SKU, đặt/đã nhận/còn lại |
| AC-02 | Nhận 7/10 sau kiểm hàng (tạo+xác nhận một hành động) | Integration | Phiếu ghi 7; tồn +7; received 7; còn 3; không ghi hàng từ chối; phiếu bất biến |
| AC-03 | Không nhận hàng | Manual/API validation | Không tạo phiếu; không đổi tồn/PO |
| AC-04 | Giao bổ sung 3 (PO đang `PARTIALLY_RECEIVED`) | Integration | Phiếu tiếp theo +3, received 10, PO `COMPLETED` |
| AC-05 | Giao dư, sai SKU, số ≤ 0, vượt remaining | API/integration | Backend từ chối: không nhận quá phần còn lại, SKU ngoài đơn, số ≤ 0 |
| AC-06 | SKU chưa có / đã có dòng tồn | PostgreSQL integration | Tạo duy nhất dòng hoặc cộng đúng số cũ, ghi movement |
| AC-07 | Xác nhận trùng, đồng thời, cạnh tranh close/cancel | PostgreSQL concurrency | Không cộng trùng/nhận vượt; kết quả trạng thái nhất quán dưới khóa |
| AC-08 | Lỗi trong lúc xác nhận hoặc retry sau timeout | Integration/fault injection | Không có cập nhật dở dang và không cộng lại |
| AC-09 | Truy cập trái quyền; sửa phiếu đã xác nhận | Security/API | Bị từ chối; Shop Owner/Accountant xem được lịch sử |

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| Xung đột purchasing import `inventory.internal` (vị trí Supplier) | Tech Lead | Code phần liên quan | OPEN — để xử lý sau theo thống nhất trong phiên |
| Đảm bảo atomic giữa cộng tồn (inventory) và `onGoodsReceiptConfirmed` (purchasing); chọn lock/version/idempotency | Tech Lead | Design Review | OPEN |
| Thêm validation "không vượt remaining" ở tầng inventory trước khi gọi contract | Tech Lead | Implementation | OPEN — contract purchasing không tự chặn |
| Ánh xạ permission cụ thể cho Shop Owner/Accountant và giới hạn theo kho | Product Owner + Security Owner | Design Review | OPEN — role đã chốt, tên permission TBD |
| Schema hiện tại, migration, đơn vị, audit | Tech Lead | Design Review | OPEN |
| HTTP contract (URI/DTO/mã lỗi) | Tech Lead | Design Review | OPEN |
| Cơ chế sửa sai số liệu sau xác nhận (ADJUSTMENT) | Product Owner + Tech Lead | Sau khi UC-32 xong | OUT OF SCOPE UC-32 — triển khai sau; UC-32 không khóa cứng |
| Mã UC quản lý PO UC-27 hay UC-34 trong SRS | Product Owner | Hoàn thiện truy vết tài liệu | OPEN |

Không phê duyệt Design Review khi còn quyết định cản trở implementation an toàn.
