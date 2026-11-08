# Combo trong báo giá

Áp dụng cho preview, tạo, sửa, xem và tạo revision báo giá; chuyển đơn dùng snapshot đã lưu.

## Request

Giữ trường chọn sản phẩm `items[].variantId` hiện có:
- SKU lẻ: gửi variantId thật.
- Combo: gửi bundleId (= Product.product_id), không cần tạo variant giả.

```json
{
  "customerId": "UUID khách hàng",
  "projectName": "Công trình",
  "vatRate": 10,
  "depositPercent": 20,
  "items": [
    {
      "variantId": "1a733086-366b-4fa3-ac6c-c81d406f5822",
      "quantity": 2,
      "discountRate": 5
    }
  ]
}
```

`POST /api/v1/sales/quotations/preview` chỉ tính thử. POST collection tạo báo giá,
PUT `/{quotationId}` sửa theo workflow hiện có. Combo phải ACTIVE, giá gói hợp lệ,
thành phần là SKU ACTIVE; quantity combo phải là số nguyên dương. Định nghĩa không hợp lệ
trả `QUOTATION_020`; ID không tồn tại vẫn dùng lỗi not-found hiện có.

## Response và giá

Một combo là một dòng gốc có `comboId`, `variantId` tương thích chứa cùng bundleId,
`sku` là mã combo thật, `variantName` là tên snapshot, `unitPrice` là giá gói và `components`
là các SKU thành phần. Các dòng SKU lẻ giữ response cũ; `comboId` null không được serialize.

Giá tổng chỉ cộng dòng gốc. Giá/số lượng thành phần phục vụ minh bạch cấu hình và kho,
không được cộng thêm lần nữa. Các mốc tiền, VAT/phí vận chuyển, quyền và hạn mức chiết khấu
giữ theo luồng báo giá hiện có.

## Khi chuyển đơn

Backend dùng đúng snapshot thành phần và giá gói đã chốt, không lấy lại giá/cấu hình combo mới.
Đơn hàng chứa SKU thật với `comboId`, số lượng SKU = số bộ × số lượng trong một bộ.
Tiền ròng của gói được phân bổ theo tỷ trọng giá SKU snapshot, làm tròn đến đơn vị tiền nhỏ nhất
và phân bổ phần dư ổn định. Tổng ròng các thành phần bằng chính xác tiền ròng của gói.

Đơn giá SKU trong đơn vẫn là giá lẻ snapshot; chiết khấu dòng gồm phần tiết kiệm của gói
và chiết khấu báo giá. Vì thế subtotal/discount của các SKU có thể khác cặp subtotal/discount
của dòng gói, nhưng net và tổng cuối cùng giữ nguyên. Không tính header thành một SKU để giữ kho.
Luồng xác minh cọc và tự giữ WH_MAIN hiện có nhận các dòng SKU thật này.

## Migration và giới hạn

- V23 map các cột combo/parent đã có, thêm tên snapshot và kiểm tra identity cho dòng ghi mới;
  không ghi lại dữ liệu lịch sử, không sửa baseline. Real-variant FK tiếp tục được giữ.
- V24 chỉ gỡ UNIQUE cũ theo riêng mã báo giá; UNIQUE(mã, phiên bản) tiếp tục chống bản trùng.
- Revision giữ snapshot combo/giá thành phần; hành vi refresh giá SKU lẻ được giữ nguyên.
- Cần chạy migration qua quy trình Flyway khi triển khai backend mới. Tác vụ phát triển không
  chạy migration trực tiếp vào database ứng dụng hoặc thay đổi combo/báo giá thật của người dùng.
# Order detail combo display

Order creation, quotation conversion and order detail responses additionally expose `displayItems`.
Frontend renders this list for the product table; existing `items` remains the flat operational SKU list.

- `itemType=SINGLE`: `item` contains the unchanged ordinary SKU response; `components=[]`.
- `itemType=COMBO`: `comboId`, `productName`, `quantity` (purchased sets), `lineTotal` (sum of actual component net totals), and `components` (real order item responses). `item=null`.
- Render a combo as name x quantity sets, expanding `components` on demand. Do not add component totals again to the combo total.
- V25 saves group/sets snapshots for new orders, including separate selections of the same combo. Catalog edits cannot change this display.
- Historical quotation orders recover sets from saved quotation roots, grouping the same combo together. Historical POS orders without a saved set count have `quantity=null`; show an unspecified count instead of guessing.
- Migration V25 is required before running this backend version. No payment, reservation or fulfillment request contract changes.

Combo display rows additionally provide `sku` (saved bundle product code), `unitPrice` per set before quotation discount, and `discountAmount`. Conversion snapshots the quotation root code and package unit price; new direct orders snapshot the code and configured package price per set. V26 adds nullable `combo_sku` and `combo_unit_price` columns. Existing quotation orders recover these fields from their saved roots; historical direct orders without snapshots show an explicit missing-value message. No current catalog price lookup is used for historical display.



## POS package-price correction (2026-11-08)
Owner explicitly approved correcting only the combo pricing portion of POS creation.
New POS combo total equals packagePrice times purchased sets. Component retail unit
prices and quantities remain snapshots; package savings are allocated as component
discounts with the same exact-cent, proportional largest-remainder algorithm used
for quotations. Component net totals sum exactly to the package total.
Order-level discounts retain their existing separate behavior. Combo display unitPrice
uses the saved package price. No stock-holding or payment behavior changes, and no
historical order is repriced.

Normal product Excel import rejects BUNDLE product codes and SKUs whose stored parent
is a BUNDLE before any product, variant, image or initial-stock event is written.
Bundle definitions continue to be managed through their dedicated API.
