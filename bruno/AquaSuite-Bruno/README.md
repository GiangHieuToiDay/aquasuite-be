# AquaSuite API Collection — Bruno

## Cách mở

1. Bruno → Open Collection → chọn thư mục `bruno/AquaSuite-Bruno` (thư mục chứa `bruno.json`).
2. Chọn environment `Local`.
3. Điền `email` và `password` của **tài khoản test SHOP_OWNER** trong environment `Local` hoặc biến runtime (không commit mật khẩu vào Git).
4. `baseUrl` mặc định là `http://localhost:8080`; thay đổi nếu server chạy ở cổng khác.

## Thứ tự chạy kiểm thử WORK-010 (Roles & Permissions)

1. **Authentication**:
   - `01 - Login`: Đăng nhập bằng tài khoản `SHOP_OWNER`. Request tự động trích xuất và gán biến runtime `{{accessToken}}` và `{{refreshToken}}`.

2. **Roles**:
   - `01 - List Roles`: Lấy danh sách 6 canonical roles. Script tự động lưu ID của role `ACCOUNTANT` (hoặc role đầu tiên) vào biến `{{roleId}}`.
   - `02 - Get Role Detail`: Xem chi tiết role theo `{{roleId}}`.
   - `03 - Get Role Detail - Not Found (404 IAM_003)`: Kiểm thử role không tồn tại.
   - `04 - List Roles - Unauthorized (401 AUTH_001)`: Kiểm thử truy cập khi chưa xác thực.
   - `05 - List Roles - Forbidden (403 AUTH_002)`: Kiểm thử truy cập khi actor không phải `SHOP_OWNER` (dùng `{{staffAccessToken}}`).

3. **Permissions**:
   - `01 - Create Permission`: Tạo permission kiểm thử với mã sinh tự động dạng `PERM_TEST_<timestamp>`. Script lưu `{{permissionId}}` và `{{permissionCode}}`.
   - `02 - Create Permission - Duplicate Code (409 IAM_005)`: Thử tạo lại với `{{permissionCode}}` đã tồn tại.
   - `03 - Create Permission - Invalid Format (400 IAM_001)`: Thử tạo permission bắt đầu bằng chữ số (`123_INVALID_FORMAT`).
   - `04 - Create Permission - Reserved Namespace (400 IAM_002)`: Thử tạo permission bắt đầu bằng tiền tố `ROLE_` (`ROLE_SUPER_ADMIN`).
   - `05 - List Permissions`: Phân trang, tìm kiếm và sắp xếp permission theo `{{permissionCode}}`.
   - `06 - Get Permission Detail`: Xem chi tiết permission theo `{{permissionId}}`.
   - `07 - Update Permission Description`: Cập nhật mô tả permission.
   - `08 - Create Temp Permission for Delete`: Tạo một permission tạm thời `{{tempPermissionId}}`.
   - `09 - Delete Unassigned Permission`: Xóa permission tạm thời chưa được gán vai trò (200 OK, payload bỏ qua field `data`).

4. **Role Permissions**:
   - `01 - Assign Permission to Role`: Gán `{{permissionId}}` cho `{{roleId}}`.
   - `02 - Assign Permission to Role (Idempotent)`: Gán lại quyền đã tồn tại (idempotent, không nhân đôi).
   - `03 - Delete Assigned Permission - Conflict (409 IAM_006)`: Thử xóa `{{permissionId}}` khi đang được gán cho vai trò -> trả lỗi 409 `IAM_006`.
   - `04 - Unassign Permission from Role`: Gỡ `{{permissionId}}` khỏi `{{roleId}}`.
   - `05 - Unassign Permission from Role (Idempotent)`: Gỡ lại quyền đã gỡ (idempotent).
   - `06 - Delete Permission (Cleanup)`: Xóa `{{permissionId}}` sau khi đã unassign để dọn dẹp sạch dữ liệu kiểm thử.

## Ghi chú

- Toàn bộ token và dynamic ID (`roleId`, `permissionId`, `permissionCode`, `tempPermissionId`) được quản lý qua biến runtime (`bru.setVar`), không hardcode trong mã nguồn collection.
- Không khôi phục `DevTestAuth.bru` đã bị xóa.
- Tất cả request bảo vệ sử dụng `Authorization: Bearer {{accessToken}}`.

