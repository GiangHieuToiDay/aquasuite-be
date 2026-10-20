# AquaSuite Authentication V2 — Bruno

## Cách mở

1. Giải nén `AquaSuite-Bruno.zip` vào thư mục dự án, ví dụ `aquasuite-be/bruno/AquaSuite-Bruno`.
2. Bruno → Open Collection → chọn thư mục `AquaSuite-Bruno` (thư mục chứa `bruno.json`).
3. Chọn environment `Local`, điền `email` và `password` của **tài khoản test**. Không commit các giá trị này vào Git; nên dùng secret variables / runtime variables trong Bruno.
4. Chạy lần lượt `01 - Login`, `02 - Refresh Token`, `03 - Logout`.

`baseUrl` mặc định là `http://localhost:8080`; sửa nếu server chạy ở cổng khác.

## Ghi chú

- Token được lưu bằng `bru.setVar` (runtime variable) sau Login/Refresh thành công, không lưu cố định trong file collection.
- `Logout` gửi `Authorization: Bearer {{accessToken}}`.
- Không đưa request kiểm tra revoked token vào bộ mặc định vì chưa có protected endpoint và chính sách xác thực cụ thể từ SecurityConfig. Muốn xác nhận revoke, cần xem SecurityConfig và endpoint được bảo vệ thực tế.
- Kết quả thành công phụ thuộc tài khoản test tồn tại, cấu hình security và backend đang chạy.
