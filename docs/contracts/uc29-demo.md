# UC-29 — API thử giữ hàng theo yêu cầu, cọc 20%

Phiên bản ngày 02/11/2026. Thay bản cũ chỉ dùng đơn công trình và mức 30%. Đọc [UC-29 mới dạng bảng](../specifications/UC-29-project-deposit-reservation.md#1-mục-tiêu-và-phạm-vi) và [UC-36](../specifications/UC-36-stock-reservation-management.md).

## 1. Phạm vi chạy

Chỉ bật với profile `uc29-demo` đồng thời không có `prod`/`production`. Ví dụ cùng profile local `dev` nếu dự án đang dùng:

```powershell
mvn spring-boot:run '-Dspring-boot.run.profiles=dev,uc29-demo'
```

Dùng cấu hình datasource/JWT/Redis hiện có qua biến môi trường, không ghi credential vào file/lệnh. Không tự khởi động app hoặc kết nối DB triển khai trong lần sửa này. Toàn bộ dữ liệu demo là mô phỏng; mọi view UC-29 có `simulated=true`.

| Nội dung | Bản thử hiện tại |
|---|---|
| Khách hàng | Mọi loại khách, không yêu cầu projectId hoặc đơn chính |
| Yêu cầu giữ | Phải chọn rõ dòng và số lượng, hoặc dùng luồng tạo đơn mới để giữ toàn bộ hàng vừa tạo |
| Mức cọc | Cố định tối thiểu 20%, không còn cấu hình mức demo 30% hoặc override từ client |
| Giá | Lấy từ giá cuối đã lưu trên đơn; phần chọn phân bổ tổng cuối theo tỷ trọng line_total và số lượng; cọc làm tròn lên 2 chữ số |
| Không yêu cầu giữ | Không có cọc bắt buộc, không tạo reservation; nộp cọc qua UC-29 trước khi chọn trả 409 |
| Chưa có đơn | Factory sales tạo Order/OrderItem mô phỏng từ khách ACTIVE và SKU ACTIVE theo giá catalog; không nhận đơn giá/discount từ client |
| Xác minh | Finance demo luôn bắt đầu PENDING; chỉ VERIFIED đạt mức mới cho giữ |
| Lưu trữ | Đơn demo, selection, tiền, tồn, lịch sử và idempotency trong bộ nhớ, mất khi restart, chỉ khóa một JVM |
| Dữ liệu thật | Chỉ đọc Order/OrderItem có sẵn và CRM/catalog qua public api; không save bảng orders/transactions/inventories, không migration |

Giá đàm phán/chiết khấu phải dùng luồng chốt giá UC-21/27/28; factory demo chỉ mô phỏng tạo đơn theo giá catalog hiện tại. Không dùng dữ liệu demo để duyệt xuất hàng hoặc kết luận số dư tiền thật của khách.

## 2. API khi đã có đơn

Prefix `/api/v1/sales/orders/{orderId}/deposits-reservations`. Tất cả yêu cầu theo JWT và quyền hiện có; Sales Staff chỉ được dùng đơn mình phụ trách, SHOP_OWNER/legacy admin có thể xem các đơn khác.

| Method/path | Mục đích |
|---|---|
| GET prefix | Xem request, cơ sở cọc, payment/reservation, phần chờ và lịch sử |
| POST prefix/requests | Ghi nhận khách yêu cầu giữ các dòng được chọn; chưa thu/khóa hàng |
| POST prefix/deposits | Ghi khoản cọc gắn đúng đơn/request đã chọn |
| POST prefix/synchronize | Sau xác minh, tạo/chuyển giữ đúng phần chọn; không thu thêm |
| PUT prefix/delivery-schedule | Lưu nhiều ngày giao dự kiến trong workspace demo với expectedVersion |

Endpoint `/temporary-holds` cũ đã bỏ: UC-29 mới không tự giữ miễn cọc trước khi xác minh. Các giữ tạm tồn tại từ UC khác vẫn có thể được chuyển đúng dòng sau khi đủ cọc.

### 2.1. Chọn phần hàng yêu cầu giữ

```json
{
  "requestId": "10000000-0000-4000-8000-000000000001",
  "warehouseCode": "WH_MAIN",
  "lines": [
    {"orderItemId": "20000000-0000-4000-8000-000000000001", "quantity": 4}
  ]
}
```

Chọn toàn đơn bằng cách gửi toàn bộ dòng và số lượng của đơn. Không gửi đơn giá hoặc giá trị tự tính; server kiểm tra dòng thuộc đúng đơn, không trùng/không vượt lượng, chốt snapshot giá. Phạm vi đã chốt không được âm thầm thay đổi; bản thử trả 409 và yêu cầu rà soát thay vì giữ thêm bằng cọc của phần nhỏ.

Ví dụ đơn 100 triệu gồm 10 chiếc cùng đơn giá sau chiết khấu: chọn 4 → `reservationValue=40000000`, `requiredDeposit=8000000`. Chọn cả 10 → cọc 20 triệu, dù kho chỉ đang có 6.

### 2.2. Ghi cọc

```json
{
  "requestId": "10000000-0000-4000-8000-000000000002",
  "amount": 8000000,
  "method": "BANK_TRANSFER",
  "referenceNo": "DEMO-TRANSFER-001",
  "warehouseCode": "WH_MAIN"
}
```

Method: CASH/BANK_TRANSFER. Chuyển khoản cần referenceNo; proofUrl nếu gửi là HTTPS. Khoản tiền luôn PENDING trước xác minh; tiền có thể được ghi nhận một phần nhưng chưa đạt 20% thì chưa giữ. Không thu vượt tổng đơn khi tính cả khoản PENDING và khoản mất phản hồi đang chờ đối soát.

### 2.3. Đồng bộ sau xác minh

```json
{"requestId":"10000000-0000-4000-8000-000000000003","warehouseCode":"WH_MAIN"}
```

Chỉ phân bổ các dòng/số lượng đã chọn. Nếu đã có giữ tạm hợp lệ, chuyển loại giữ cùng bản ghi, không cộng lại lượng cũ; không chuyển giữ của dòng chưa được chọn. Thiếu hàng trả phần chờ, không giảm tồn thực. Giữ có cọc không tự hết hạn theo ngày giao.

### 2.4. Lịch giao dự kiến

```json
{
  "expectedVersion": 0,
  "deliveries": [
    {"date":"2026-12-10","note":"Dot 1"},
    {"date":"2027-01-10","note":"Dot 2"}
  ]
}
```

Tối đa 24 mục, ngày không ở quá khứ theo Asia/Ho_Chi_Minh. Chưa nối lịch persistent hoặc UC-40. Sai version trả 409.

## 3. API khi chưa có đơn

```text
POST /api/v1/sales/deposit-reservation-orders
```

```json
{
  "requestId": "10000000-0000-4000-8000-000000000010",
  "customerId": "30000000-0000-4000-8000-000000000001",
  "lines": [
    {"variantId":"40000000-0000-4000-8000-000000000001","quantity":10}
  ],
  "amount": 2000000,
  "method": "CASH",
  "warehouseCode": "WH_MAIN"
}
```

Giá tính từ catalog, không phải amount client. Thứ tự: tạo Order/OrderItem demo → chọn toàn bộ hàng vừa tạo → ghi cọc PENDING. Nếu số nộp chưa đủ 20% giá trị server tính, vẫn chờ bổ sung/xác minh, không giữ ngay. Retry cùng requestId/payload dùng lại đúng Order/payment, kể cả lần trước đã tạo đơn nhưng lỗi phản hồi thanh toán. Khi đã có đơn, dùng API mục 2 để tránh tạo một đơn khác.

## 4. Fixture của module phụ thuộc

| API | Quyền | Dữ liệu |
|---|---|---|
| POST `/api/v1/dev/uc29/inventory-stock` | Owner/legacy admin | warehouseCode, variantId, onHandQty; chỉ tồn demo hữu hạn, mặc định 0 |
| POST `/api/v1/dev/uc29/payments/{paymentId}/verification` | Owner/Accountant | status VERIFIED hoặc REJECTED; không đổi ngược kết quả đã chốt |

Stock fixture:

```json
{"warehouseCode":"WH_MAIN","variantId":"40000000-0000-4000-8000-000000000001","onHandQty":6}
```

Verification fixture:

```json
{"status":"VERIFIED"}
```

Sau verify, gọi synchronize trên orderId nhận trong response hoặc retry POST cọc/create với cùng key và payload cũ. Không có listener/batch tự đồng bộ ở bản thử. Không tạo job UC-36, UC-39 xuất hàng hoặc quy trình hoàn tiền.

## 5. Response, trạng thái và lỗi

| Field | Ý nghĩa |
|---|---|
| reservationRequested | Đã có khách yêu cầu giữ hay chưa; không suy từ khách công trình |
| reservationValue / requiredDeposit | Giá trị phần chọn / mức cọc 20%; 0 khi chưa có request |
| depositSatisfied | Có request và tiền VERIFIED đã đạt yêu cầu; không phải quyền xuất hàng |
| lines[].orderedQty / requestedQty | Số mua trong đơn / số khách yêu cầu giữ |
| lines[].heldQty / waitingQty | Số đang giữ / số đã yêu cầu nhưng chưa được phân bổ; không tính phần đơn chưa yêu cầu giữ vào waiting |
| totalAmount / outstandingAmount | Tổng đơn / số còn phải thu của cả đơn sau tiền VERIFIED, không chỉ phần hàng giữ |
| reservationSyncStatus | NOT_REQUESTED, AWAITING_DEPOSIT, WAITING_VERIFICATION, BELOW_REQUIRED_DEPOSIT, RESERVATION_PENDING, APPLIED hoặc trạng thái lỗi tiền |

APPLIED không nghĩa đã giữ đủ; phải xem waitingQty. Retry dùng cùng key và payload, key khác vẫn không vượt nhu cầu chọn. Không tái sử dụng key cho đơn/nội dung khác.

| Code | HTTP | Ý nghĩa |
|---|---|---|
| SALES_DEPOSIT_001 | 404 | Không có Order |
| SALES_DEPOSIT_002 | 409 | Đơn/trạng thái/giá chưa hợp lệ; không liên quan điều kiện projectId |
| SALES_DEPOSIT_003 | 400 | Khoản tiền, dòng/số lượng, kho hoặc lịch không hợp lệ |
| SALES_DEPOSIT_004 | 409 | Key dùng lại sai payload/đơn |
| SALES_DEPOSIT_005 | 409 | Sai version lịch |
| SALES_DEPOSIT_006 | 503 | Lỗi module phụ thuộc; retry cùng key, không thu thêm |
| SALES_DEPOSIT_007 | 409 | Chưa có yêu cầu giữ, không được thu cọc theo UC-29 |
| SALES_DEPOSIT_008 | 409 | Phạm vi/giá đã chốt thay đổi hoặc lượng đang giữ vượt phạm vi mới |
| AUTH_002 | 403 | Không có quyền/không phụ trách đơn |
| SYS_002 / SYS_003 | 400 | Binding/validation theo convention |
| DB_001 | 409 | Trùng reference hoặc thay trạng thái tiền đã chốt ở simulator |

## 6. Kiểm thử và thay adapter thật

Các test mới kiểm tra khách không có công trình, không yêu cầu giữ, 20% toàn/phần đơn, chống sai phạm vi/giá, tạo đơn và retry, pending/verified, thiếu hàng và profile isolation. Full Maven test hiện còn bị chặn bởi import DTO combo cũ trong AuthenticationMergeIT; harness `.agent/work/uc29-verification/pom.xml` chỉ compile bộ test UC-29 với cùng source/dependencies, không sửa root POM hoặc test cũ.

```powershell
mvn -o -q -f .agent/work/uc29-verification/pom.xml '-Dtest=ProjectDeposit*Test,DemoProject*Test' test
```

Production cần Order creation service thật, persistent selection/operation/reservation/ledger, đối soát và allocations finance, transaction/recovery và review permission/contract. Adapter finance/inventory phải ở module sở hữu; UC-36 dùng lại service reservation, không tạo một kho giữ thứ hai. Chưa được coi việc bỏ @Profile là hoàn thành production.

Kết quả ngày 02/11/2026: **34 test UC-29/demo pass**, 0 failure/error/skipped; main compile thành công. Lệnh test tại root vẫn dừng ở lỗi compile của AuthenticationMergeIT nêu trên, chưa chứng nhận toàn bộ test suite.
