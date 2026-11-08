# Order Payments - UC-23

Bộ request kiểm thử các API thanh toán tại quầy hiện có. Các request ghi nhận và tải chứng từ **thay đổi dữ liệu**: chỉ dùng môi trường kiểm thử và đơn kiểm thử. UC-70 và checkout online chưa nằm trong bộ này.

## Preparation

1. Chạy backend, chọn environment `Local` trong Bruno và đăng nhập bằng tài khoản `SALES_STAFF` hoặc `SHOP_OWNER` qua `Authentication/01 - Login`. Request lưu `accessToken` trong phiên Bruno. Không ghi token hoặc mật khẩu thật vào file chia sẻ.
2. Tạo hoặc chọn một đơn kiểm thử có thể nhận thanh toán, còn nợ ít nhất 300.000 đồng. Điền UUID của đơn vào `paymentOrderId` trong environment. Mặc định tiền mặt là 100.000 và chuyển khoản là 200.000; có thể đổi `paymentCashAmount` và `paymentBankAmount` cho phù hợp với đơn.
3. Sinh hai UUID **khác nhau**, chưa từng sử dụng, và điền vào `paymentCashKey` và `paymentBankKey`. Ví dụ, chạy `[guid]::NewGuid()` hai lần trong PowerShell. Giữ nguyên key, số tiền, nội dung và tài khoản khi kiểm tra gửi lại; khi bắt đầu kịch bản mới hãy dùng key mới và đơn kiểm thử phù hợp.
4. Với kiểm tra phân quyền 11–12, đăng nhập riêng bằng `ACCOUNTANT` và đặt token hợp lệ vào biến phiên `accountantAccessToken` trong Bruno; không lưu token thật vào repository. Dùng lại `accessToken` của nhân viên cho các request còn lại.

## Run Order

- Chạy 01 để xem tổng trước khi thu; chạy 02 ghi tiền mặt `VERIFIED` và 03 gửi lại cùng yêu cầu để kiểm tra không tạo giao dịch trùng.
- Chạy 04 ghi chuyển khoản `PENDING`, rồi 05–06 kiểm tra lịch sử và tổng thanh toán. Khoản chờ thể hiện riêng, chưa làm giảm số còn phải trả. `paymentCashId` và `paymentBankId` được lưu tự động trong phiên sau khi request 02 và 04 thành công.
- Với 07, mở tab Body trong Bruno và chọn file PNG, JPEG hoặc PDF không quá 5 MB cho trường `file`; đường dẫn mặc định chỉ là placeholder. Mỗi khoản chuyển khoản đang chờ chỉ nhận một chứng từ. Chạy 08 để tải lại.
- Chạy 09–10 để kiểm tra thay đổi nội dung với key cũ và số tiền không hợp lệ. Chạy 11–12 khi đã thiết lập token kế toán.

Chạy theo thứ tự trong cùng phiên Bruno. Không chạy cả thư mục tự động khi chưa chọn file, chưa có token kế toán hoặc chưa chuẩn bị đơn. Gửi lại 02 hoặc 04 với key cũ chỉ kiểm tra idempotency; để tạo khoản thu khác phải dùng key mới. Các assertion phản ánh code hiện tại, không thay thế code review hay kiểm thử giao dịch đồng thời.
