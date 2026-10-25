# Quy tắc nghiệp vụ thẩm định Báo giá dự án (Service-Level Validation Rules)

Tài liệu này ghi nhận các quy tắc nghiệp vụ cần database truy vấn và kiểm tra chéo module, do đó được **hoãn lại để xử lý ở tầng Service** thay vì validate trong Bean Validation của DTO:

---

## 1. Không trùng lặp SKU trên các dòng chi tiết (Unique SKU Constraint)
- **Quy tắc**: Trong cùng một báo giá (`quotation`), một biến thể sản phẩm (`variantId`) chỉ được xuất hiện một lần trên danh sách dòng gốc (`parent_item_id IS NULL`).
- **Lý do hoãn**: DTO chỉ nhận danh sách, việc kiểm tra tính duy nhất có thể thực hiện trước nhưng service sẽ kiểm tra tổng thể khi liên kết với cấu trúc combo component.

---

## 2. Kiểm tra tính toàn vẹn số lượng theo đơn vị tính (Unit-Specific Quantity Step)
- **Quy tắc**:
  - Đối với đơn vị rời rạc (ví dụ: *cái*, *bộ*, *chiếc*, *thùng*...): Số lượng đặt (`quantity`) bắt buộc phải là **số nguyên dương** (`scale = 0` hoặc phần thập phân bằng 0).
  - Đối với đơn vị đo lường liên tục (ví dụ: *mét*, *kg*, *m²*...): Cho phép tối đa **2 chữ số thập phân**.
- **Lý do hoãn**: Đơn vị tính (`unit`) thuộc quyền sở hữu của module `Catalog`. DTO đầu vào không chứa trường `unit`, tầng Service sẽ truy vấn `CatalogApi` để lấy đơn vị chuẩn của từng SKU và thực hiện validation này.

---

## 3. Trạng thái hoạt động của SKU và Khách hàng (Active Entity Invariant)
- **Quy tắc**:
  - Khách hàng (`customerId`): Phải tồn tại trong module `CRM` và có trạng thái `ACTIVE`.
  - Biến thể sản phẩm (`variantId`): Phải tồn tại trong module `Catalog` và có trạng thái `ACTIVE` (không được phép thêm SKU đã ngưng kinh doanh vào báo giá mới).
- **Lý do hoãn**: DTO chỉ nhận UUID theo quy chuẩn Modular Monolith (không phụ thuộc chéo entity). Service sẽ gọi synchronous `CrmApi` và `CatalogApi` để xác thực trạng thái.

---

## 4. Hạn hiệu lực bắt buộc khi phát hành (ValidUntil on Submit/Send)
- **Quy tắc**: Khi lưu nháp (`DRAFT`), trường `validUntil` có thể tạm rỗng (hệ thống tự lấy mặc định `quotationDate + 30 ngày`). Tuy nhiên, khi chuyển trạng thái sang `PENDING_APPROVAL` hoặc `SENT`, `validUntil` bắt buộc phải có giá trị và phải lớn hơn hoặc bằng `quotationDate`.
- **Lý do hoãn**: `QuotationUpsertRequest` dùng chung cho cả tạo nháp và tính thử, do đó không đặt ràng buộc `@NotNull` cưỡng chế tại DTO.
