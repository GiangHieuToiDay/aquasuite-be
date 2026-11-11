# UC30 MVP - API thử thu COD tiền mặt và bàn giao

Backend only. Cần delivery thật trong `deliveries`, `delivery_status=DELIVERED`, `cod_required=true`, `cod_amount>0`, đúng `shipper_id`. Chưa bổ sung CRUD UC41 hay frontend. Migration V28/V29 được Flyway áp dụng khi ứng dụng khởi động; không tự ghi nhận COD lịch sử. Theo quyết định 09/11/2026, COD không dùng ảnh ở cả bước khai thu và bàn giao.

Base: `/api/v1/finance/cod`. Mọi endpoint cần authentication hiện có. HTTP 200 + `ApiResponse<T>` khi thành công. Lỗi theo GlobalExceptionHandler. POST collections nhận application/json; không còn multipart hoặc endpoint proof trong COD.

| Method / Path | Quyền | Ý nghĩa |
|---|---|---|
| POST `/collections` | SHIPPER đúng đợt giao | JSON khai số thực thu, thời điểm và ghi chú |
| GET `/collections` | SHIPPER own; ACCOUNTANT/SALES_STAFF/SHOP_OWNER all | Lọc shipperId, orderId, status=SUBMITTED/ACCEPTED/REJECTED; page=0, size=20 (1..100) |
| GET `/collections/{id}` | Quyền xem + ownership | Chi tiết số khai, số đã nộp/còn giữ, version |
| POST `/collections/{id}/accept` | ACCOUNTANT khác shipper | Xác nhận số khai thu; chưa tạo receipt/chưa PAID |
| POST `/collections/{id}/reject` | ACCOUNTANT khác shipper | Bác khai thu với reason bắt buộc; giữ lịch sử |
| POST `/remittances` | ACCOUNTANT khác shipper | Kiểm đếm và nhận tiền; không cần ảnh bàn giao |
| GET `/collections/{id}/remittances` | Quyền xem + ownership | Lịch sử phiếu, từng dòng và transactionId |
| GET `/orders/{orderId}/balance` | ACCOUNTANT/SALES_STAFF/SHOP_OWNER | Tiền cửa hàng chưa nhận / shipper còn giữ / khách còn phải trả |

### 1. Shipper khai thu tiền mặt

Request body có Content-Type `application/json`:

```json
{
  "deliveryId": "11111111-1111-4111-8111-111111111111",
  "amount": 22220000,
  "collectedAt": "2026-11-08T15:00:00+07:00",
  "note": "Khách giao đủ tiền mặt",
  "idempotencyKey": "22222222-2222-4222-8222-222222222222"
}
```

UUID là ví dụ; dùng ID delivery thật và key mới cho mỗi lần khai. Số tiền VND nguyên đồng. Không gửi file ở bước khai thu hoặc nhận bàn giao. Gửi lại cùng key phải giữ nguyên actor và metadata; cùng key khác nội dung trả COD_004.

Kết quả collection có collectionId, deliveryId/orderId/customerId/shipperId, amount, expectedAmount, reviewStatus, reviewedBy/At, rejectReason, version, remittedAmount, heldAmount, remittanceStatus. Khai mới là SUBMITTED; chưa ghi transactions.

### 2. Kế toán xác nhận khai thu

Kiểm tra số khai thu, đơn/đợt giao và đối chiếu với shipper trước khi ACCEPT. Đây là xác nhận thủ công, không phải kết luận từ ảnh:

```json
{"expectedVersion": 0}
```

Bác khai thu:

```json
{"expectedVersion": 0, "reason": "Số khai thu cần kiểm tra lại với shipper"}
```

Lấy version mới trong response. ACCEPTED xác nhận số khai thu và trách nhiệm shipper; không tăng VERIFIED. REJECTED không tính vào số còn giữ và không chiếm hạn mức khai thu của đợt; khai lại bằng key mới, không ghi đè lịch sử.

### 3. Kế toán nhận tiền về cửa hàng

```json
{
  "idempotencyKey": "33333333-3333-4333-8333-333333333333",
  "shipperId": "44444444-4444-4444-8444-444444444444",
  "amount": 22000000,
  "items": [
    {
      "collectionId": "55555555-5555-4555-8555-555555555555",
      "amount": 22000000,
      "expectedVersion": 1
    }
  ],
  "note": "Thực nhận 22 triệu; shipper còn thiếu 220.000đ"
}
```

Thay UUID bằng dữ liệu thật. Từ 1..50 dòng khác collectionId, cùng shipper; tổng dòng phải bằng amount thực nhận và không vượt số còn giữ. Chỉ ACCEPTED được nhận tiền. Mỗi dòng tạo một RECEIPT VERIFIED paymentType=COD, liên kết order/customer/invoice từ server. Header phiếu không gán tùy ý một khách khi có nhiều khách. Response có remittanceId, shipperId, receivedBy/At, amount, note và items chứa collectionId/orderId/transactionId/amount.

Timeout: gửi lại cùng request/key. Sau nhận một phần, lấy collection version mới rồi nộp bổ sung với key mới. Không sửa/xóa phiếu đã chốt. Tiền thừa, tổng sai hoặc số dư đổi: từ chối; kiểm tra lại trước khi xác nhận. Muốn ghi thiếu thì nhận đúng số thực kiểm đếm, ghi note nguyên nhân; phần thiếu vẫn mở.

### 4. Số dư và công nợ

- `storeOutstanding`: tổng nghĩa vụ đơn trừ RECEIPT VERIFIED; chỉ bằng 0 thì thanh toán hết.
- `shipperHeld`: COD ACCEPTED trừ phân bổ cửa hàng đã nhận; phần này thuộc shipper.
- `customerCollectible`: max(storeOutstanding - shipperHeld, 0).
- `pendingClaimAmount`: shipper khai nhưng bằng chứng chưa được chấp nhận; hiển thị chờ kiểm tra.

Ví dụ thu đủ 22.220.000đ, cửa hàng nhận 22.000.000đ: đơn PARTIALLY_PAID, storeOutstanding=shipperHeld=220.000, customerCollectible=0. Nộp tiếp 220.000đ thì PAID. Cọc và chuyển khoản VERIFIED cũ vẫn được tính vào tổng đơn đúng một lần.

UC24 bổ sung `codHeldAmount`, `customerCollectibleAmount`, `codReviewRequired`. Khi còn SUBMITTED hoặc tiền shipper chưa nộp, debtStatus=`COD_REVIEW` (trừ khách đã bị BLOCKED thủ công). `outstandingBalance` vẫn là mức tiền cửa hàng chưa nhận để giữ nguyên semantics/hạn mức; FE phải dùng các trường mới để mô tả trách nhiệm. overdue/invoice amounts lúc COD_REVIEW là số nội bộ chưa đối soát, không dùng để đòi/phạt khách. Credit-check, blacklist-status, reminder, export/email statement bị chặn với COD_003 cho đến khi đối soát xong. Không tự chuyển tiền shipper giữ thành VERIFIED để giảm số nợ.

### Lỗi và giới hạn thử nghiệm

ACCEPT kiểm tra lại delivery dưới khóa sau khi khóa đơn: phải còn DELIVERED/codRequired=true, số COD dương và bằng expectedAmount lúc khai, delivery vẫn thuộc đúng đơn và collection vẫn cùng khách. Dữ liệu lệch trả COD_002, giữ SUBMITTED/version cũ; kế toán kiểm tra lại, có thể REJECT với lý do nếu khai báo sai. Không tự sửa số tiền hoặc đánh dấu đã thu. Việc đổi shipper không chuyển khoản đã khai sang người mới; tiếp tục truy trách nhiệm người thực thu ban đầu. Khoản đã ACCEPTED và bàn giao/replay dùng snapshot đã chốt, không bị khóa nhận tiền chỉ vì delivery thay đổi sau đó.

Nếu khách thanh toán trực tiếp lúc COD còn SUBMITTED, khoản COD không tự trở thành ACCEPTED. Khi phần nghĩa vụ không còn đủ để ACCEPT, đối chiếu hai nguồn trước; nếu xác định khai trùng/sai thì REJECT với lý do, không xóa hoặc tự bác khai báo. COD_REVIEW còn mở cho tới khi xử lý xong. Không REJECT một khoản thu có thật chỉ để bỏ cảnh báo; tranh chấp/tiền thừa thật chờ quy trình ngoại lệ.

- COD_001 / 400: dữ liệu khai thu hoặc bàn giao không hợp lệ.
- COD_002 / 409: trạng thái/version/số dư thay đổi, chưa ACCEPTED hoặc nhận vượt khoản còn giữ.
- COD_003 / 409: cần đối soát COD trước xử lý công nợ khách.
- COD_004 / 409: key được dùng khác nội dung.
- AUTH_002 / 403: sai quyền/shipper hoặc tự duyệt/nhận tiền của mình.
- SYS_004 / 404: không có tài nguyên; SYS_003 / 400 cho Bean Validation.
- DB_001 / 409: chặn sửa tổng/khách hoặc hủy đơn khi còn COD mở (trigger PostgreSQL).

Không chỉnh/đảo phiếu, xử lý tiền thừa, bù trừ phí hay đối soát legacy trong bản thử. Các trường hợp này cần quy trình riêng trước dùng production; không sửa thẳng số đã xác nhận để xử lý.

V29 chỉ gỡ NOT NULL cho cột ảnh cũ để insert collection không cần ảnh; giữ nguyên ảnh lịch sử nếu đã có. API mới không lưu/đọc ảnh COD. Không thay đổi proof của chuyển khoản UC23/UC70.

### Các điểm chờ UC khác (10/11/2026)

| Phụ thuộc | UC30 đã sẵn | Điều kiện tích hợp còn chờ |
|---|---|---|
| UC39/40/41/43 — xuất/giao/phân công | Public DeliveryCodQueryApi và POST collections dùng delivery thật | Owner fulfillment xây API nghiệp vụ tạo/cập nhật delivery, phân công và số COD. Mốc DELIVERED không tự tạo RECEIPT hoặc PAID. Không seed giả delivery để thay luồng production. |
| UC26 — hủy/giảm | Trigger chặn sửa tổng/khách hoặc hủy orders khi COD còn mở | Owner UC26 bổ sung kiểm tra COD khi duyệt/chuyển trạng thái request, vì PENDING_CANCEL của request không phải orders.order_status. Không tự giải quyết COD hoặc hoàn tiền trong UC30. |
| UC63 — phân bổ hóa đơn | Mỗi dòng nhận bàn giao có transactionId RECEIPT VERIFIED | Phân bổ khoản thu hiện có vào hóa đơn; không tạo thêm transaction cho cùng tiền. invoiceId STORE chưa phải phân bổ hóa đơn điện tử. |
| UC37/64 — trả hàng/hoàn/đảo | Giữ phiếu, số dư và lịch sử bất biến | Owner định nghĩa chứng từ đối ứng/hoàn tiền và điều chỉnh trách nhiệm. Không sửa/xóa khoản đã chốt. |

Đợt gia cố này không thêm endpoint, migration hoặc thay đổi semantics UC23/24/70. Chưa nghiệm thu giao hàng end-to-end hoặc production; lỗi tiền thừa/legacy/đảo phiếu vẫn cần đầu mối xử lý trước rollout.

Giao thức khóa khi tích hợp: order → delivery cho các thao tác cần cả hai nguồn, giữ khóa tới commit. Nguồn delivery không tự chuyển tiền COD thành VERIFIED; chỉ thao tác nhận bàn giao của Finance tạo receipt.
