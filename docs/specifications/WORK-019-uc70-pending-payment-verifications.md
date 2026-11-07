# WORK-019 — UC-70 Process Pending Payment Verifications

## Metadata

- **Work ID:** WORK-019
- **Use Case:** UC-70 — Process Pending Payment Verifications
- **Owner / Decision Owner:** Nhóm phụ trách tài chính, Product Owner và Tech Lead; chưa xác định cá nhân.
- **Owning Module:** `finance`; phối hợp `sales` qua public contract.
- **Risk:** L — liên quan tiền, phân quyền, trạng thái đơn và thao tác đồng thời.
- **Design Review:** PENDING. Đây là dự thảo để thống nhất và review, chưa phải thiết kế đã duyệt hoặc chức năng đã hoàn thành.

## 1. Goal and Scope

### Problem and Desired Outcome

Kế toán cần xem các khoản chuyển khoản đang chờ, đối chiếu với thông tin thực nhận và ghi nhận quyết định. Nếu xác minh thành công, hệ thống tính khoản đó vào tiền đã thu của đơn; nếu từ chối, hệ thống không tính khoản đó là tiền đã thu và lưu lý do.

### Current State

- UC-23 đã ghi khoản `RECEIPT` / `BANK_TRANSFER` / `PENDING` trong `transactions`; `PENDING` chưa làm giảm số còn phải trả. Tiền mặt `VERIFIED` tính là đã trả ngay.
- UC-23 cho phép nhiều khoản chờ và khoản thanh toán khác cùng tồn tại. Tại thời điểm đối chiếu, số còn phải trả có thể khác lúc tạo khoản chờ.
- UC-23 có API xem lịch sử và tổng thanh toán theo đơn, API đọc chứng từ theo đơn/giao dịch; chứng từ tối đa một file trong `order_payment_proofs`.
- `transactions` đã có `approved_by`, `approved_at`, `status`, `reference_no` và `description`. Sự hiện diện các trường không có nghĩa UC-70, lưu lý do từ chối hoặc nhật ký quyết định đã có.
- Sales cung cấp `lockOrderPaymentInfo` và `updatePaymentStatus`; UC-23 ghi tiền dưới khóa đơn trong cùng transaction. Cần review contract chung trước khi tái sử dụng hoặc điều chỉnh.
- WORK-018 đang `IN_PROGRESS`, Design Review `PENDING` theo trạng thái local đã kiểm tra; cần xác minh lại trạng thái và code tại thời điểm review UC-70.

### In Scope

- BE cho danh sách/lọc và xem chi tiết các khoản chuyển khoản đang chờ; tái sử dụng lịch sử, tổng đơn và chứng từ UC-23 khi phù hợp.
- Kế toán xác minh hoặc từ chối đúng một giao dịch; ghi nhận người xử lý, thời điểm và lý do từ chối theo thiết kế được duyệt.
- Khi xác minh hợp lệ: kiểm tra lại số tiền đã thu và nghĩa vụ của đúng đơn, chuyển giao dịch `VERIFIED`, cập nhật `sales.paymentStatus` nguyên tử và để UC-24 đọc được khoản đã xác minh.
- Phân quyền, kiểm thử API, chống quyết định trùng và xử lý đồng thời; tài liệu hướng dẫn kết nối theo API thực tế sau khi hoàn thành.

### Out of Scope

- FE, checkout UC-18, COD, thẻ, hoàn tiền và xử lý hủy/điều chỉnh đơn của UC-26. Phạm vi webhook đang cần chốt theo nhà cung cấp và lựa chọn tự xác minh hay kế toán xác minh; chưa khẳng định đã được triển khai hoặc loại khỏi UC-70.
- Phát hành hóa đơn điện tử, tự phân bổ hoặc kế toán phân bổ UC-63; UC-70 không tạo giao dịch thu thứ hai và không tự chuyển tiền giữa đơn.
- Sửa test, schema hoặc quy tắc báo giá của thành viên khác. Hai lỗi full suite thuộc báo giá vẫn là kết quả FAIL đã biết và được bàn giao riêng.

## 2. Actors and Security

| Actor | Quyền dự kiến | Giới hạn |
|---|---|---|
| Accountant | Xem, xác minh hoặc từ chối khoản chuyển khoản đang chờ. | Không trực tiếp ghi nhận tiền tại quầy theo quyết định UC-23. |
| Shop Owner | Xem thanh toán theo quyền UC-23 hiện có. | Không được xác minh hoặc từ chối UC-70; có thể tự xử lý trường hợp vượt nghĩa vụ ngoài hệ thống. |
| Sales Staff | Xem các khoản của đơn qua API UC-23 theo quyền hiện có. | Không mặc định được xác minh/từ chối. |

- Tất cả endpoint phải xác thực; kiểm tra quyền ở backend, không dựa vào việc ẩn nút ở FE.
- Người xử lý lấy từ phiên đăng nhập, không nhận `actorId` do client tự gửi.
- Chứng từ chỉ được truy cập qua API có kiểm soát quyền; không ghi nội dung chứng từ, token hoặc thông tin ngân hàng không cần thiết vào log.
- Người dùng chốt chỉ Accountant được thực hiện quyết định xác minh hoặc từ chối thủ công; Sales Staff và Shop Owner không có quyền quyết định UC-70.

## 3. Business Rules

1. Chỉ khoản thu của đơn có `transactionType=RECEIPT`, `paymentMethod=BANK_TRANSFER`, `status=PENDING` mới là đối tượng quyết định của UC-70. Giao dịch tiền mặt và giao dịch đã xử lý không được xử lý như khoản chờ mới.
2. Kế toán đối chiếu thông tin giao dịch, đơn, mã tham chiếu/chứng từ nếu có và tiền thực nhận. Một ảnh chứng từ hoặc lời khai của khách không tự động chứng minh ngân hàng đã nhận tiền.
3. `PENDING` chưa tính vào số đã trả. Chỉ khi quyết định xác minh hợp lệ được commit, khoản đó mới thành `VERIFIED` và được tính một lần vào số đã thu của đơn.
4. Trước quyết định xác minh phải khóa đơn, đọc lại tổng đơn và các khoản `VERIFIED`; không trừ các khoản `PENDING` khác. Nếu khoản đang chờ vượt số còn phải trả, hệ thống báo không thể xác minh, giữ nguyên `PENDING` và thông tin giao dịch. Kế toán hoặc chủ cửa hàng tự đối chiếu, giải quyết với khách ngoài chức năng này. Hệ thống không tự chuyển sang đơn khác, không tạo giao dịch hoàn tiền và không xóa thông tin đã nhận.
5. Từ chối: ghi `REJECTED`, người xử lý, thời điểm và lý do bắt buộc; không thay đổi tổng tiền đã xác minh. Nội dung, độ dài và danh mục lý do cần thiết kế, nhưng không được cho phép bỏ trống.
6. Không thay đổi số tiền, khách, đơn, phương thức hoặc giao dịch gốc để che sai lệch. Cách điều chỉnh khi phát hiện sai thông tin khai báo và cơ chế sửa quyết định đã xử lý cần quyết định riêng.
7. Công thức tại thời điểm tính: `verifiedPaid = tổng RECEIPT / VERIFIED của đơn`; `remaining = max(totalAmount - verifiedPaid, 0)`; `pendingAmount = tổng RECEIPT / PENDING`. Trạng thái đơn là `UNPAID`, `PARTIALLY_PAID` hoặc `PAID` theo tổng đã xác minh. Không cộng `PENDING` vào đã trả.
8. Tiền chỉ thuộc đơn của giao dịch; không tự chuyển khoản dư sang đơn khác. Người phụ trách UC-26 đang xây dựng luồng hủy/điều chỉnh; tạm thời chưa chốt hành vi xác minh cho đơn hủy, cần phối hợp trước khi tích hợp hoặc phát hành luồng này.
9. Trước/sau quyết định phải có khả năng truy vết actor, thời điểm, giao dịch, trạng thái và lý do; không để lỗi ghi nhật ký khiến dữ liệu và quyết định bị lệch. Nơi lưu và contract audit cần review, không mặc định event sau commit là bền vững.

| Trạng thái | Hành động | Trạng thái kết quả dự kiến | Điều kiện |
|---|---|---|---|
| PENDING | Xác minh | VERIFIED | Có quyền, cùng đơn/khách, số hợp lệ và không vượt nghĩa vụ còn lại sau khi khóa. |
| PENDING | Từ chối | REJECTED | Có quyền; lý do hợp lệ; không tính là đã thu. |
| VERIFIED / REJECTED | Gửi lại hoặc quyết định khác | Chưa chốt chính sách response | Không được tính tiền hai lần hoặc ghi đè quyết định âm thầm. |
| PENDING | Yêu cầu xác minh khoản vượt số còn phải trả | PENDING | Báo lỗi, giữ giao dịch; kế toán hoặc chủ cửa hàng tự xử lý ngoài hệ thống. |

### Examples

1. Đơn 10 triệu, có chuyển khoản chờ 7 triệu và đã thu tiền mặt 3 triệu: kế toán đối chiếu đúng, xác minh 7 triệu thì tổng `VERIFIED` là 10 triệu, đơn `PAID`.
2. Đơn 10 triệu có hai khoản chờ 7 triệu và 6 triệu: cả hai có thể cùng `PENDING`. Nếu khoản 7 triệu được xác minh trước thì đơn còn 3 triệu. Khi yêu cầu xác minh khoản 6 triệu, hệ thống báo vượt số còn phải trả và giữ khoản đó `PENDING`; kế toán hoặc chủ cửa hàng tự xử lý với khách ngoài hệ thống.
3. Đơn 10 triệu, khách mới khai báo chuyển khoản 7 triệu nhưng kế toán chưa thấy tiền: khoản này chưa phải tiền đã thu. Quyết định giữ chờ hay từ chối tùy tình huống và quy tắc từ chối được nhóm thống nhất.

## 4. Data and Migration Decisions

- Finance sở hữu giao dịch và kết quả xác minh; Sales sở hữu đơn và trạng thái thanh toán. Tham chiếu chéo module bằng scalar UUID, không JPA association sang module khác.
- Đề xuất kiểm tra khả năng dùng `approved_by`/`approved_at` cho người/thời điểm xác minh; `description` hiện là ghi chú nhân viên, không mặc định ghi đè thành lý do từ chối. Cần thiết kế trường hoặc bản ghi quyết định riêng cho `REJECTED`, lịch sử và audit.
- Cần kiểm tra `transactions.status` và ràng buộc DB có hỗ trợ `REJECTED`, index danh sách chờ, actor, thời điểm, lỗi dữ liệu cũ và ảnh hưởng migration. Không tự sửa/xóa dữ liệu hoặc viết lại migration đã áp dụng.
- Dữ liệu nhóm hiện là mockup, không yêu cầu tương thích nghiệp vụ với dữ liệu cũ; không suy ra được phép xóa/reset database. Migration vẫn phải có đường triển khai, rollback/recovery và kiểm thử.
- Phân bổ từng phần UC-63 là dữ liệu riêng chưa triển khai. `invoiceId` gắn hóa đơn cửa hàng không đồng nghĩa đã phân bổ vào hóa đơn điện tử.

## 5. Contracts

### HTTP API — Proposed, Not Implemented

| Method / Path đề xuất | Mục đích | Quyền / lưu ý |
|---|---|---|
| `GET /api/v1/finance/payment-verifications` | Danh sách khoản chuyển khoản chờ; lọc theo đơn, khách, thời gian và phân trang theo contract được thiết kế. | Chỉ Accountant. |
| `GET /api/v1/finance/payment-verifications/{paymentId}` | Xem chi tiết khoản chờ, thông tin cần đối chiếu và đơn liên quan. | Không trả thông tin nhạy cảm không cần thiết. |
| `POST /api/v1/finance/payment-verifications/{paymentId}/verify` | Xác minh một khoản chờ. | Backend lấy actor; kiểm tra trạng thái và nghĩa vụ mới nhất. |
| `POST /api/v1/finance/payment-verifications/{paymentId}/reject` | Từ chối một khoản chờ. | Lý do bắt buộc; định dạng chi tiết cần thiết kế. |

Tên endpoint, DTO, mã lỗi và response là phương án để review, chưa phải API thực tế. Thiết kế cần quy định 400/401/403/404/409, trạng thái đã xử lý, quá nghĩa vụ, đơn bị hủy, sai dữ liệu và cách retry; không trả raw lỗi database. Chứng từ dùng API UC-23 hiện tại nếu contract và quyền phù hợp, tránh lộ URL riêng.

### Inter-Module Java API

- Dự kiến Finance lấy khóa đơn qua `SalesOrderApi.lockOrderPaymentInfo(orderId)` và cập nhật trạng thái qua `updatePaymentStatus` trong cùng transaction. Cần review thời điểm gọi, visibility của giao dịch đã flush, propagation, connection và rollback; không import `sales.internal`.
- Không thay đổi contract công khai hay tạo domain event chỉ vì thuận tiện. Nếu thay đổi contract phải mô tả caller, khả năng tương thích và test.

## 6. Concurrency, Transaction and Failure Safety

1. Đề xuất thứ tự khóa: khóa đơn trước khi đọc số dư hoặc quyết định, sau đó khóa/kiểm tra giao dịch thuộc đơn; đọc lại trạng thái và các khoản đã xác minh khi đang giữ khóa. Đối chiếu với UC-23 (khóa key rồi khóa đơn) và UC-26, tránh vòng khóa ngược/deadlock. Không mặc định cách lấy orderId ban đầu là dữ liệu đáng tin cậy nếu chưa kiểm tra lại.
2. Quyết định `PENDING → VERIFIED`, thông tin xử lý và cập nhật trạng thái Sales phải commit/rollback cùng transaction. Không dựa riêng vào event sau commit để đồng bộ tiền.
3. Hai kế toán xử lý cùng giao dịch: chỉ một quyết định có hiệu lực; quyết định còn lại phải nhận kết quả được định nghĩa. Hai khoản khác nhau cùng đơn phải tính lại sau khi lấy khóa, không để tổng đã xác minh vượt nghĩa vụ.
4. Retry do timeout: thiết kế để không tạo khoản thu mới hoặc cộng hai lần; phân biệt gửi lại cùng quyết định và quyết định trái ngược. Hình thức idempotency/response còn OPEN.
5. Nếu cập nhật Sales hoặc lưu kết quả/audit bắt buộc thất bại, rollback phần thuộc cùng transaction. Thiết kế retry và đối soát khi hệ thống gặp lỗi hoặc nghĩa vụ đơn thay đổi qua luồng khác; không khẳng định luồng UC-26/UC-70 hiện đã dùng cùng khóa.
6. Chứng từ chỉ hỗ trợ đối chiếu; upload/xem lỗi không được tự xác minh. Khi cần đánh dấu ngoại lệ tiền thực nhận, nếu trả lỗi API thì phải thiết kế cách lưu trạng thái/ghi chú không bị rollback ngoài ý muốn.

## 7. Acceptance Criteria and Verification

| AC | Kịch bản | Kết quả cần chứng minh |
|---|---|---|
| AC-01 | Accountant xem danh sách khoản chuyển khoản chờ và chi tiết đúng đơn | Lọc/phân trang đúng, không lẫn tiền mặt hoặc khoản đã xử lý; quyền và dữ liệu trả về đúng. |
| AC-02 | Xác minh 7 triệu khi đơn 10 triệu đã thu tiền mặt 3 triệu | Một transaction thành VERIFIED, tổng đã trả 10 triệu, còn 0, đơn PAID; UC-24 thấy khoản đã xác minh. |
| AC-03 | Từ chối giao dịch chờ với lý do hợp lệ | REJECTED, lưu actor/thời điểm/lý do, không làm tăng đã trả. |
| AC-04 | Giao dịch không tồn tại, sai loại, sai trạng thái, hoặc thiếu quyền | Từ chối an toàn với lỗi theo contract; dữ liệu tiền không thay đổi. |
| AC-05 | Hai kế toán cùng xác minh/từ chối một khoản | Chỉ một quyết định có hiệu lực; không cộng trùng; response còn lại theo chính sách retry được duyệt. |
| AC-06 | Hai khoản PENDING 7 và 6 triệu của đơn 10 triệu được xử lý đồng thời | Một khoản được xác minh; khoản còn lại vượt số còn phải trả, nhận lỗi và giữ nguyên PENDING; tổng VERIFIED không vượt 10 triệu và không mất thông tin giao dịch. |
| AC-07 | Ghi tiền UC-23 và xác minh UC-70 cùng đơn đồng thời | Sau khi khóa và tính lại, tổng VERIFIED không vượt nghĩa vụ; trạng thái Sales nhất quán. |
| AC-08 | Cập nhật Sales/lưu quyết định thất bại | Transaction rollback đúng; không có khoản VERIFIED với trạng thái đơn bị cập nhật dở. |
| AC-09 | Chứng từ không có, sai đơn, người không quyền hoặc lỗi đọc | Áp dụng chính sách chứng từ được duyệt; không tự xác minh hoặc lộ dữ liệu. |
| AC-10 | Request gửi lại, quyết định đối lập, thay đổi/hủy đơn | Không ghi đè hoặc đếm trùng; hành vi cụ thể và test sau khi chốt các quyết định mở. |

Kiểm thử dự kiến: unit, API/security, PostgreSQL Testcontainers cho khóa/rollback và tình huống concurrent, migration nếu có, Bruno trên database tạm và full suite. Các lỗi test báo giá đã biết ghi riêng là ngoài phạm vi; không báo full suite PASS nếu chúng vẫn lỗi.

## 8. Open Decisions

| Quyết định | Phương án cần nhóm xác nhận | Cần trước |
|---|---|---|
| Lý do từ chối | Đã chốt bắt buộc; còn thiết kế danh mục, độ dài, nơi lưu bền vững và nội dung hợp lệ. | Design Review |
| Webhook và bằng chứng | Người dùng dự định dùng webhook; cần xác định nhà cung cấp, cơ chế xác thực, nội dung sự kiện, đối chiếu đúng đơn, chống trùng và quyết định tự xác minh hay để kế toán xác minh. Chưa chốt ảnh chứng từ là bắt buộc. | Design Review / implementation |
| Retry và quyết định đã xử lý | Cùng quyết định trả thành công idempotent hay 409; quyết định trái ngược, cho phép sửa/đảo hay không. | Design Review |
| Đơn hủy, điều chỉnh hoặc tổng nghĩa vụ thay đổi | Người phụ trách khác đang làm UC-26; tạm hoãn quyết định và phối hợp trước tích hợp/phát hành các tình huống này. | Trước tích hợp UC-26 / phát hành |
| Audit và lưu quyết định | Dùng bảng/contract nào cho actor, thời điểm, lý do; tính bền vững và migration. | Design Review |
| Danh sách và search | Bộ lọc, sắp xếp, phân trang và thông tin khách/đơn cần hiển thị. | Design Review |
