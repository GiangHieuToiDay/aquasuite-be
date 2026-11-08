# Goods Receipts — UC-32 Quản lý nhập kho theo đơn mua

Bộ request kiểm thử nghiệp vụ nhập kho (UC-32, Hướng B: tạo = xác nhận, phiếu bất biến).

## Chuẩn bị

1. Chạy backend ở `http://localhost:8080` (biến `baseUrl` trong environment `Local`).
2. Có sẵn một **đơn mua hàng (PO) ở trạng thái `CONFIRMED` hoặc `PARTIALLY_RECEIVED`** và còn hàng phải nhận.
3. Set biến môi trường `grPurchaseOrderId` = id của PO đó.
   (Có thể lấy id PO từ collection Purchasing / màn hình đơn mua hàng.)

## Thứ tự chạy

| Seq | Request | Mục đích |
|-----|---------|----------|
| 00 | Login Shop Owner | Lấy `accessToken` (owner@aquasuite.local / 123456) |
| 01 | Get Receivable PO | Lấy dòng còn phải nhận; tự lưu `grPoItemId`, `grVariantId`, `grRemaining` |
| 02 | Create & Confirm Receipt | Nhập `grReceiveQty` (mặc định 1) cho dòng đầu; lưu `grReceiptId` |
| 03 | Get Receipt Detail | Xem chi tiết phiếu vừa tạo |
| 04 | List Receipts By PO | Lịch sử phiếu của PO |
| 05 | List Receipts Paged | Danh sách phiếu phân trang |
| 06 | Reject Exceed Remaining | Nhận 999999 → 400 (INV_016 / PO_020) |
| 07 | Reject Item Not In PO | poItemId lạ → 400 (INV_014) |
| 08 | Reject Zero Quantity | quantity 0 → 400 (validation / INV_015) |

Chạy 00 → 01 → 02 trước; các bước còn lại dùng biến do chúng set ra.

## Biến môi trường dùng ở đây

- `grPurchaseOrderId` — **phải tự set** trước khi chạy.
- `grReceiveQty` — số lượng nhận ở bước 02 (mặc định 1, phải ≤ `grRemaining`).
- `grPoItemId`, `grVariantId`, `grRemaining`, `grReceiptId` — tự điền bởi script.

## Quyền

Mọi endpoint yêu cầu role **SHOP_OWNER** hoặc **ACCOUNTANT**.

## Giao bổ sung nhiều đợt

Sau bước 02, PO chuyển `PARTIALLY_RECEIVED`. Chạy lại 01 → 02 để nhập đợt tiếp;
khi mọi dòng nhận đủ, PO tự chuyển `COMPLETED`.
