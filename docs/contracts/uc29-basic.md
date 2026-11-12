# UC29 cơ bản — đơn có sẵn

API lưu PostgreSQL, không cần profile `uc29-demo`. Selection/reservation dùng V15–V17, xác minh thanh toán dùng V19; V20 bổ sung kho đã chốt và hàng đợi tự phân bổ. Migration chỉ được kiểm chứng trên DB Docker tạm; không chạy thủ công trên DB ứng dụng trong lần triển khai này.

## Luồng sử dụng

Cập nhật theo quyết định người dùng 08/11/2026: khi chuyển báo giá có `depositPercent > 0`
(ví dụ 20), backend tự bật `reserveStockAfterDeposit` và đăng ký toàn bộ dòng đơn tại kho chính
`WH_MAIN`. Không cần FE gửi cờ hoặc nhân viên chọn lại dòng/kho cho trường hợp này.
Chưa đủ tiền VERIFIED thì chưa giữ; Finance xác nhận đủ cọc của chính đơn thì tự phân bổ.
Tỷ lệ lấy từ trường `depositPercent`, không phân tích chữ trong ghi chú. Không đổi các đơn lịch sử.

Trang **Cọc và giữ hàng** lấy danh sách qua
`GET /api/v1/sales/orders?depositReservationOnly=true&page=0&size=20`.
Bộ lọc chạy trước phân trang/count: đơn CONFIRMED/PROCESSING và Finance xác định có mức cọc
cần thu (`payment.depositRequired > 0`). Hiển thị cả chưa cọc, PENDING, chưa đủ và đã đủ cọc;
không giới hạn tỷ lệ 20%. Response mỗi dòng có `payment` và `depositStatus`:
`AWAITING_DEPOSIT` → **Chưa đủ cọc**, `SATISFIED` → **Đủ cọc**.
FE dùng `payment.verifiedPaid` là tiền đã xác minh, `payment.pendingAmount` là đang chờ,
và hiển thị thiếu cọc bằng `max(payment.depositRequired - payment.verifiedPaid, 0)`.
Các trường mới chỉ được trả khi bật bộ lọc quản lý; JSON danh sách thường giữ nguyên.
Không bắt buộc đơn lịch sử đã lưu cờ/selection để hiển thị; điều đó không tự tạo giữ hàng cho đơn cũ.
Bỏ qua/false giữ danh sách đơn thông thường. V22 vẫn lưu cờ phục vụ luồng tạo/chuyển đơn.
FE gọi GET stock-reservations của đơn để lấy `payment`; không yêu cầu nhập lại mã giao dịch.
Endpoint xác minh vẫn giữ quyền và luồng UC70 hiện có.

1. Lấy orderId và orderItemId từ đơn đang CONFIRMED/PROCESSING. Khách không cần projectId. Khi tạo đơn UC21 hoặc chuyển báo giá UC28 cho luồng cọc, gửi `reserveStockAfterDeposit: true` để chưa giữ tồn theo luồng legacy. Bỏ qua/false giữ hành vi cũ.
2. POST requests để chốt dòng/số lượng. Server tính giá trị phần chọn theo tổng cuối của đơn, cọc tối thiểu 20% (làm tròn lên hai số).
3. Nếu chưa đủ tiền VERIFIED, nhận AWAITING_DEPOSIT; yêu cầu được lưu nhưng chưa giữ tồn. Thu/xác minh tiền vẫn qua nghiệp vụ finance, API này không nhận tiền hoặc sửa trạng thái xác minh.
4. Khi UC70 xác minh đủ cọc hoặc UC23 ghi tiền mặt VERIFIED, hệ thống tự phân bổ đúng kho/phạm vi đã chốt sau khi giao dịch tiền commit. Không cần bấm synchronize để kích hoạt. Hệ thống giữ phần kho có sẵn; phần thiếu tiếp tục chờ. Sau khi nhập hàng, có thể dùng synchronize với key mới để bổ sung phần thiếu.
5. GET tải dữ liệu mới. Key cũ không phân bổ thêm hàng khi nhập bổ sung; key mới cũng không được vượt mục tiêu đã chốt.

## Kết quả cọc từ Finance

UC29 dùng `OrderReceivableApi.getOrderReceivable(orderId)` có sẵn từ SCRUM19. `depositRequired` là mức cọc đã thỏa thuận trên đơn (hoặc mốc đầu tiên theo Finance); `depositSatisfied` là quyết định duy nhất cho phép phân bổ giữ hàng. Không tính lại 20% phần chọn, không tự cộng receipt hoặc ghi `orders.paid_amount`.

Ví dụ cọc thỏa thuận 30 triệu: Finance xác minh 20 triệu thì chưa giữ; thêm 10 triệu và Finance trả `depositSatisfied=true` thì tự giữ phần hàng đã chốt. Thiếu cọc không giữ một phần; thiếu tồn khi đã đủ cọc có thể giữ một phần và chờ phần còn lại. Đơn chưa có mức cọc được Finance trả false; UC29 không tự tạo ngưỡng.

Không tạo API xác minh cọc riêng. Dùng cùng giao dịch thu của UC23 và quyết định xác minh của UC70:

| Bước | API | Người thực hiện / kết quả |
|---|---|---|
| Chốt phần hàng, báo mức cọc | `POST /api/v1/sales/orders/{orderId}/stock-reservations/requests` | Nhân viên phụ trách đơn hoặc owner/admin; chưa đủ tiền thì `AWAITING_DEPOSIT` |
| Khai báo khoản cọc | `POST /api/v1/finance/orders/{orderId}/payments` | SALES_STAFF/SHOP_OWNER; dùng `BANK_TRANSFER`, số tiền và `idempotencyKey`; tạo `PENDING` |
| Xem khoản chờ | `GET /api/v1/finance/payment-verifications?orderId={orderId}&status=PENDING` | ACCOUNTANT |
| Xác minh khoản cọc | `POST /api/v1/finance/payment-verifications/{paymentId}/verify` | ACCOUNTANT; cùng khoản thu chuyển thành `VERIFIED` |
| Từ chối khoản cọc | `POST /api/v1/finance/payment-verifications/{paymentId}/reject` | ACCOUNTANT; body `{"reason":"Không thấy tiền về"}`; khoản thu thành `REJECTED` |
| Tự giữ hàng | Backend xử lý sau commit của khoản VERIFIED | Dùng selection/kho/actor đã xác nhận; không cần POST thêm |
| Thử lại phân bổ lỗi | `POST /api/v1/sales/orders/{orderId}/stock-reservations/allocations/{allocationId}/retry` | Nhân viên phụ trách đơn hoặc owner/admin; giữ nguyên ID thao tác |
| Bổ sung giữ khi nhập thêm hàng | `POST /api/v1/sales/orders/{orderId}/stock-reservations/synchronize` | Cùng selection/kho, requestId mới |

`paymentId` là `transactionId` do API ghi nhận khoản thu trả về. Xác minh không tạo khoản thu mới. `PENDING` và `REJECTED` không làm đủ cọc; nhiều khoản `VERIFIED` được cộng lại. Tiền mặt do UC23 ghi nhận là `VERIFIED` ngay theo quyền thu tiền hiện có.

Kế toán dùng nguyên API xác minh của SCRUM19; Sales ghi hàng đợi trong cùng transaction cập nhật trạng thái thanh toán, rồi tự giữ hàng trong transaction mới sau commit. GET không ghi kho. Nếu kho chỉ đủ một phần thì `PARTIALLY_HELD`, phần thiếu chờ hàng; tồn vật lý không đổi. Lỗi kho không đảo khoản VERIFIED: `depositStatus=SATISFIED` và `allocationStatus=FAILED`, kèm `allocationId`/`allocationErrorCode`. Retry đúng ID không thu lại và không giữ trùng. Tác vụ định kỳ mặc định mỗi 30 giây phục hồi các hàng đợi lỗi/chưa xử lý, kể cả sau restart. Có thể tắt riêng worker định kỳ bằng `aquasuite.reservations.retry-enabled=false`; xử lý ngay sau xác minh vẫn hoạt động.

Prefix: `/api/v1/sales/orders/{orderId}/stock-reservations`.

| Method/path | Nội dung |
|---|---|
| GET prefix | Xem yêu cầu, tiền xác minh, cọc tối thiểu và từng reservation |
| POST prefix/requests | Lưu yêu cầu; giữ ngay nếu đã đủ cọc |
| POST prefix/synchronize | Thực hiện lại phân bổ cho yêu cầu đã lưu sau xác minh/nhập hàng |
| POST prefix/allocations/{allocationId}/retry | Thử lại đúng tác vụ tự phân bổ, không cần body |

Cả hai POST dùng body:

```json
{
  "requestId": "10000000-0000-4000-8000-000000000001",
  "warehouseCode": "WH_MAIN",
  "lines": [
    {
      "orderItemId": "20000000-0000-4000-8000-000000000001",
      "quantity": 10
    }
  ]
}
```

Các ID ví dụ phải thay bằng ID của đơn thật. Không gửi actor, giá hoặc số tiền. Quyền: SHOP_OWNER/ADMIN/SUPER_ADMIN, hoặc SALES_STAFF phụ trách đúng đơn; JWT bắt buộc theo security hiện có.

## Kết quả và retry

Theo yêu cầu trạng thái số lượng SKU: kho có 10 chiếc, giữ 3 chiếc thì `reservations[].status=HELD`, `heldQty=3`. Kiểm tra lại qua `GET /api/v1/inventory/stock-reservations/variants/{variantId}` trả kho tương ứng có `onHandQty=10`, `heldQty=3`, `availableQty=7`, `quantities=[{"status":"HELD","quantity":3},{"status":"AVAILABLE","quantity":7}]`. Lần giữ tiếp theo chỉ được lấy từ 7 chiếc khả dụng. API đọc này dành cho owner/admin hoặc SALES_STAFF như lát cắt UC29, không phải endpoint tạo giữ.

Status của reservation áp cho lượng thực đang giữ: HELD nếu heldQty>0; WAITING nếu chưa giữ được và còn thiếu; CONSUMED hoặc RELEASED khi không còn giữ/chờ. heldQty/waitingQty vẫn hiển thị riêng khi chỉ phân bổ được một phần. Trạng thái được đọc từ số lượng bền vững, không mất khi restart và không sửa trạng thái ACTIVE của catalog. Nếu hàng có serial, bản cơ bản chưa chọn định danh từng chiếc; reservation quản lý số lượng của SKU/kho.

Response theo `ApiResponse<OrderStockReservationView>`. Các trường tiền dùng nguyên DTO public của Finance trong `payment`:

```json
{
  "reservationRequested": true,
  "reservationValue": 40000000,
  "payment": {
    "orderId": "UUID đơn",
    "totalAmount": 100000000,
    "verifiedPaid": 20000000,
    "pendingAmount": 10000000,
    "remaining": 80000000,
    "paymentStatus": "PARTIAL",
    "depositRequired": 30000000,
    "depositSatisfied": false
  },
  "depositStatus": "PARTIALLY_PAID",
  "status": "AWAITING_DEPOSIT",
  "allocationStatus": "AWAITING_DEPOSIT"
}
```

FE dùng `payment.depositRequired`, `payment.verifiedPaid`, `payment.pendingAmount`, `payment.remaining`, `payment.totalAmount`, `payment.depositSatisfied`. Bỏ các alias cũ `requiredDeposit`, `verifiedPaidAmount`, `outstandingAmount`, `depositPercent`, `depositShortfall` ở cấp ngoài. Nếu cần hiển thị còn thiếu cọc: `max(payment.depositRequired - payment.verifiedPaid, 0)`; quyết định giữ vẫn đọc boolean của Finance.

`reservationValue` chỉ mô tả phần hàng chọn, không quyết định ngưỡng cọc. `lines` là phạm vi chốt; `reservations` là ledger tồn. `depositStatus` phục vụ nhãn giao diện; `allocationStatus`, `allocationId`, `allocationErrorCode` phục vụ theo dõi/thử lại tác vụ. `warehouseCode` là kho đã chốt; `lineStatuses` chứa orderItemId, requestedQty, heldQty, waitingQty và trạng thái từng dòng. COMPLETED có thể chỉ phân bổ một phần do thiếu tồn.

`status` và lượng tồn là kết quả giữ hàng; `depositStatus` là kết quả tài chính. Đủ cọc không đồng nghĩa đã giữ đủ hoặc được phép xuất kho. Danh mục sản phẩm không bị đổi ACTIVE thành HELD.

- NOT_REQUESTED: chưa chốt yêu cầu.
- AWAITING_DEPOSIT: tiền VERIFIED chưa đủ mức. Nếu đã có giữ trước đó, xem lượng cụ thể; trạng thái này không tự giải phóng hàng.
- RESERVATION_PENDING: tiền đã đủ, tác vụ phân bổ chưa hoàn tất; worker tự xử lý hoặc nhân viên retry.
- WAITING_STOCK/PARTIALLY_HELD/HELD: chưa có hàng giữ, giữ một phần, hoặc đủ phần yêu cầu.
- RELEASED/CONSUMED: phản ánh dữ liệu ledger khi có năng lực xử lý tương ứng; bản cơ bản không mở các thao tác đó.

Retry cùng key/phạm vi không tạo thêm selection hoặc giữ trùng; trả view hiện tại. Thứ tự dòng và số 10/10.00 được chuẩn hóa. Key khác vẫn giữ cùng mục tiêu cố định. Đổi order/action/kho/dòng/số lượng cho key cũ trả SALES_DEPOSIT_004 (409). Đổi phạm vi hoặc snapshot giá sau khi chốt trả SALES_DEPOSIT_008 (409); không tự sửa bằng client.

Tiền và trạng thái đủ cọc chỉ đọc từ public API Finance; UC29 không ghi payment_status hoặc paid_amount và không tạo khoản thu.

## Giới hạn cần biết

- UC21 hiện giữ tồn tổng mà chưa lưu chủ sở hữu vào ledger. Với SKU còn lượng tổng không khớp chi tiết ledger ở bất kỳ kho nào, UC29 trả INV_RES_005 (409) để đối chiếu. Không tự giải phóng tổng, nhận giữ cũ là của đơn đang chọn, hoặc cộng thêm giữ trùng. Đơn hiện có từ luồng legacy có thể bị chặn vì lý do này; cần xử lý nguồn giữ cũ trước khi dùng API giữ thật.
- Không gỡ giới hạn trên bằng cách sửa reserved_qty trực tiếp. Việc chuyển giữ legacy cần nghiệp vụ đối chiếu riêng.
- Theo giới hạn sở hữu code của người dùng, UC29/UC36 không sửa implementation release/dispatch/điều chỉnh tồn dùng chung. Các luồng đó chưa tích hợp ledger; chưa chứng nhận tính nhất quán khi chạy đồng thời với nghiệp vụ giải phóng/xuất ở UC khác. Cần tích hợp bởi owner tương ứng trước khi vận hành các luồng đó cùng UC29.
- Chưa có đổi/hủy phạm vi, hoàn cọc, lịch giao hoặc xuất hàng. Không coi đủ cọc/HELD là quyền xuất.
- Không dùng fixture tiền/tồn demo cho các endpoint này. Yêu cầu và khóa retry lưu DB, không mất khi restart.
- V20 khôi phục kho cho yêu cầu cũ chỉ khi các operation REQUEST ghi nhận cùng một kho; không có hoặc mâu thuẫn dữ liệu thì để trống và cần nhân viên xác nhận lại qua yêu cầu hợp lệ. Không tự nhận giữ legacy hoặc tự phân bổ lịch sử tại thời điểm chạy migration.
# Reservation management payment entry update

The deposit/reservation detail screen is read-only for payments: show the Finance summary and receipt history, then navigate to `/app/orders/{orderId}` to record payment. It must not offer a second receipt form or issue a Finance payment POST itself.

Backend stock-reservations endpoints only read/reserve/synchronize/retry allocation; there is no dedicated receipt-writing endpoint to remove. Keep the shared Finance order payment endpoints and verified-payment event handling: those power order detail payments and automatic allocation after sufficient verified deposit. Existing Finance receipt records remain unchanged. This update supersedes earlier instructions to enter receipts directly within reservation management.



## Scoped deferred-holding correction (2026-11-08)
Owner restricted this correction to UC29/UC36. Order creation, legacy inventory
reserve/release/dispatch methods, Finance and catalog remain unchanged.

For persisted reserveStockAfterDeposit=true only, UC29 uses
StockReservationApi.reserveDeferredDeposit. The persisted flag establishes that
creation did not reserve this order's items. Existing aggregate-only quantities
therefore remain reserved by other workflows and excluded from availability.
Only the incremental hold for this order is recorded in its detailed ledger.
A positive difference between aggregate reserved stock and recorded holds no
longer blocks this deferred flow. A negative difference still returns INV_RES_005.
Existing inventory locks and atomic writes protect allocation; replay does not
duplicate holds. Synchronize can fill waiting quantity only from newly available stock.

Non-deferred orders retain exact reconciliation and continue to reject ambiguous
legacy ownership. No automatic history backfill, aggregate reset or adoption is
performed. This supersedes the blanket legacy-mismatch restriction above only for
persisted deferred orders. Releasing a UC29 hold affects only that hold's quantity.
