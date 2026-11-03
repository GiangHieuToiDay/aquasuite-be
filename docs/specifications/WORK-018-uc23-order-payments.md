# WORK-018 — UC-23 Manage Order Payments

## Metadata

- **Work ID**: WORK-018
- **Feature / Use Case**: UC-23 — Manage Payment and Payment Verification
- **Version**: Dự thảo 0.1
- **Owner / Decision Owner**: Product Owner và Tech Lead — chưa xác định cụ thể
- **Owning Module**: `finance`; `sales` sở hữu đơn hàng và trạng thái thanh toán
- **Risk Reasons**: Tiền, phân quyền, xử lý đồng thời, hợp đồng công khai, liên module và migration
- **Design Review**: PENDING — chưa được duyệt triển khai.

## 1. Goal and Scope

Cho phép nhân viên có quyền ghi nhận tiền mặt đã nhận hoặc khoản chuyển khoản khách khai báo cho đơn hàng hợp lệ, xem lịch sử và tổng quan thanh toán. Chỉ tiền vào đã `VERIFIED` được tính là đã trả. UC-21 và luồng online cung cấp đơn; UC-70 xác minh khoản chuyển khoản đang chờ.

### In Scope

- Xem lịch sử, tổng quan và ghi nhận nhiều đợt thanh toán cho một đơn.
- Tiền mặt thực nhận do nhân viên có quyền ghi nhận là `VERIFIED`; chuyển khoản khách khai báo là `PENDING`, lưu mã tham chiếu và chứng từ nếu có.
- Tính số tiền đã xác minh, đang chờ, còn phải trả và trạng thái thanh toán; cập nhật `sales` qua hợp đồng liên module được duyệt.
- Phân quyền, lưu thông tin thao tác, chống ghi trùng, xử lý đồng thời và kiểm thử phù hợp.

### Out of Scope

- Đối soát, xác nhận hoặc từ chối chuyển khoản của UC-70; phân bổ nhiều đơn UC-63; hoàn tiền, bút toán đảo; thu và nộp COD; duyệt mua nợ; checkout; quyết định giao hàng; FE; thanh toán thẻ hoặc máy quẹt thẻ.
- Tài liệu chưa xác lập việc xác minh tự động qua cổng ngân hàng hoặc webhook.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role/Permission | Data Restrictions |
|---|---|---|---|
| Nhân viên có quyền | Xem thanh toán đơn; ghi nhận tiền mặt hoặc chuyển khoản | Mã quyền và vai trò cụ thể chưa chốt | Chỉ truy cập dữ liệu được phép |
| Kế toán / người có quyền | Xem giao dịch liên quan | Chính sách quyền chưa chốt | Hạn chế truy cập chứng từ |

Bắt buộc xác thực và kiểm tra quyền tại BE. Chỉ người được phép thu tiền mới ghi tiền mặt thành `VERIFIED` ngay khi thực nhận. Lưu người thao tác và thời điểm; không ghi secret, nội dung chứng từ hoặc dữ liệu ngân hàng/khách hàng không cần thiết vào log. Cơ chế tải lên, lưu trữ và kiểm soát truy cập chứng từ chưa chốt.

## 3. Business Rules

1. Đơn phải tồn tại, đủ điều kiện nhận thanh toán và cung cấp số tiền phải thu hiện hành từ `sales`. Trạng thái đơn/hóa đơn được phép và cách xử lý đơn điều chỉnh còn phải quyết định.
2. Số tiền phải dương, phù hợp đơn vị tiền và độ chính xác được hỗ trợ. Số phải thu lấy từ `sales`, không tin giá trị client gửi. Chính sách đơn vị tiền và độ chính xác cần xác nhận.
3. Mỗi phương thức và mỗi đợt thanh toán là một giao dịch tiền vào riêng gắn với mã đơn. Tiền mặt thực nhận được tạo `VERIFIED`; chuyển khoản khách khai báo được tạo `PENDING`. Ảnh hoặc mã tham chiếu không tự chứng minh đã nhận tiền.
4. `verifiedPaid` là tổng giao dịch tiền vào hợp lệ `VERIFIED`; `pendingAmount` là tổng giao dịch tiền vào hợp lệ `PENDING`; `remaining = max(payableTotal - verifiedPaid, 0)`. Cách xử lý hàng trả, điều chỉnh và sổ hiện có cần được chốt trước khi triển khai.
5. Với tổng phải thu dương: chưa có tiền xác minh là `UNPAID`; đã xác minh một phần là `PARTIALLY_PAID`; đã xác minh bằng tổng phải thu là `PAID`. Đơn giá trị 0, trả thừa và nhiều khoản đang chờ cần quyết định riêng.
6. `PENDING` không làm giảm số còn phải trả và không được tính là đã thu. Chọn COD hoặc mua nợ không tự tạo giao dịch `VERIFIED`. Đơn COD và mua nợ được duyệt có thể xuất kho, giao hàng trước khi trả đủ theo quy tắc giao hàng riêng; UC-23 không phê duyệt xuất kho hay đổi trạng thái thực hiện đơn.
7. Bảo toàn lịch sử tài chính và người/thời điểm thao tác. Không âm thầm sửa hoặc xóa giao dịch đã xác minh; điều chỉnh và đảo giao dịch cần luồng riêng được duyệt.

| From | Action | To | Conditions/Invariants |
|---|---|---|---|
| Mới | Ghi nhận tiền mặt | VERIFIED | Đã thực nhận và người thu có quyền |
| Mới | Ghi nhận khách khai báo chuyển khoản | PENDING | Chưa khẳng định đã nhận tiền |
| PENDING | Kế toán xác minh | VERIFIED hoặc REJECTED | Thuộc UC-70; nêu để xác định ranh giới tích hợp |

## 4. Data and Migration Decisions

- `finance.transactions` hiện có `orderId` dạng scalar, số tiền, phương thức, URL chứng từ, trạng thái và người thao tác; `sales.orders` có tổng tiền và trạng thái thanh toán. Các trường này chưa chứng minh service thanh toán đã triển khai.
- Không tạo liên kết JPA chéo module hoặc import `internal` của module khác. Hợp đồng công khai dùng ID và DTO.
- Có thể cần migration bổ sung cho chống ghi trùng, metadata chứng từ hoặc chỉ mục. Schema, ràng buộc duy nhất, tương thích dữ liệu cũ, backfill và rollback cần review; không viết lại baseline migration.
- Không diễn giải lại lịch sử tài chính khi chưa có migration được review.

## 5. Contracts

Tên và đường dẫn dưới đây chỉ là đề xuất, chưa được triển khai hoặc phê duyệt. Cấu trúc HTTP response và lỗi theo `docs/contracts/api-conventions.md`.

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| GET `/api/v1/finance/orders/{orderId}/payment-summary` | Quyền xem, chưa chốt mã | Đơn được phép truy cập | Tổng tiền và trạng thái | Chưa xác thực, thiếu quyền, không có đơn |
| GET `/api/v1/finance/orders/{orderId}/payments` | Quyền xem, chưa chốt mã | Đơn hợp lệ; phân trang chưa chốt | Lịch sử giao dịch | Chưa xác thực, thiếu quyền, không có đơn |
| POST `/api/v1/finance/orders/{orderId}/payments` | Quyền ghi nhận, chưa chốt mã | Số tiền, CASH/BANK_TRANSFER, tham chiếu/chứng từ khi áp dụng, khóa chống ghi trùng; validation chưa chốt | Giao dịch và tổng quan | Số tiền/trạng thái không hợp lệ, trùng xung đột, lỗi quyền hoặc đơn |

Giao diện tải chứng từ và trường tham chiếu bắt buộc chưa chốt. Không để lộ thông tin truy cập dịch vụ lưu trữ hoặc URL chứng từ công khai khi chưa có chính sách được duyệt.

### Inter-Module Java API

| Provider | Consumer | Method/DTO | Transaction | Errors/Compatibility |
|---|---|---|---|---|
| sales | finance | Đề xuất đọc/kiểm tra snapshot đơn gồm ID, số phải thu, trạng thái và phiên bản | Nhất quán và khóa chưa chốt | Không có đơn, không đủ điều kiện, đơn đã thay đổi |
| finance / sales | sales / finance | Đề xuất cập nhật hoặc phát sự kiện trạng thái thanh toán | Chiến lược chưa chốt | Cần xác định retry, trạng thái cũ và phục hồi |

### Domain Event

`OrderPaymentStatusChangedEvent` là phương án kiến trúc chưa được duyệt. Nếu chọn, phải xác định payload/phiên bản, thời điểm commit, chống xử lý trùng, thứ tự, retry, theo dõi lỗi và đối soát. `docs/architecture/module-contracts.md` vẫn để mở cơ chế nhất quán `finance` → `sales`.

## 6. Concurrency, Transaction and Failure Safety

- Gửi lại cùng yêu cầu không được tạo hai giao dịch. Chốt phạm vi khóa chống trùng, ràng buộc duy nhất, cách trả kết quả khi gửi lại và khi dữ liệu xung đột.
- Ghi tiền mặt đồng thời hoặc thay đổi tổng đơn không được làm sai số dư. Chọn cơ chế khóa/phiên bản theo đơn và ranh giới transaction với `sales`; không nhầm khoản đang chờ với tiền đã nhận.
- Lưu thanh toán và cơ chế cập nhật trạng thái `sales` nhất quán, có khả năng phục hồi. Xác định retry và đối soát khi cập nhật liên module thất bại; không mặc định listener sau commit là đủ tin cậy.
- Xác định cách xử lý khi lưu chứng từ thành công nhưng ghi database thất bại, hoặc ngược lại; bảo vệ quyền truy cập chứng từ.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Người có quyền ghi tiền mặt thực nhận | Kiểm thử tích hợp và phân quyền | Một giao dịch VERIFIED, tổng quan/trạng thái được cập nhật |
| AC-02 | Ghi nhận chuyển khoản khách khai báo | Kiểm thử tích hợp | PENDING; số đã xác minh và số còn thiếu không đổi |
| AC-03 | Nhiều đợt, kết hợp phương thức | Kiểm thử tích hợp | Lịch sử riêng, tổng hợp chính xác |
| AC-04 | Đơn, số tiền hoặc quyền không hợp lệ | Kiểm thử validation và bảo mật | Từ chối, không thay đổi sổ giao dịch |
| AC-05 | Gửi trùng và ghi nhận đồng thời | Kiểm thử đồng thời/chống trùng trên PostgreSQL | Không tạo trùng hoặc tính sai số đã xác minh |
| AC-06 | Cập nhật finance → sales thất bại | Kiểm thử tích hợp lỗi/phục hồi | Phục hồi bền vững theo thiết kế được duyệt |
| AC-07 | COD/mua nợ và thực hiện đơn | Kiểm thử ranh giới | Không tự tạo VERIFIED hay đổi trạng thái thực hiện đơn |
| AC-08 | Truy cập chứng từ | Kiểm thử quyền và lỗi lưu trữ | Chỉ người có quyền truy cập; xử lý lỗi theo chính sách được duyệt |

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| Trạng thái đơn/hóa đơn được phép, đơn điều chỉnh và đơn giá trị 0 | Product Owner / phụ trách Sales | Design Review | OPEN — UC-21/22 và BA cũ mâu thuẫn hoặc thiếu |
| Contract sales–finance, cạnh tranh khi đổi tổng đơn và đồng bộ đáng tin cậy | Tech Lead Sales / Finance | Design Review | OPEN — kiến trúc để mở đối soát |
| Mã quyền cụ thể, quyền Accountant và Shop Owner | Product Owner / phụ trách IAM | Design Review | OPEN — ma trận quyền và UC cũ khác nhau |
| Độ chính xác số tiền, trả thừa và nhiều khoản Pending cạnh tranh | Product Owner / phụ trách Finance | Design Review | OPEN |
| Mã tham chiếu/chứng từ bắt buộc, lưu trữ và truy cập hạn chế | Product Owner / phụ trách Security | Design Review | OPEN |
| Biên nhận UC-23, phiếu thu UC-63 và tích hợp audit | Phụ trách Finance | Design Review | OPEN — tránh ghi tiền hai lần |
| Phân loại dữ liệu hiện có, migration và phục hồi | Phụ trách Finance / Database | Design Review | OPEN |

Không duyệt thiết kế hoặc triển khai các phần bị ảnh hưởng khi các quyết định trên còn cản trở thiết kế an toàn.
