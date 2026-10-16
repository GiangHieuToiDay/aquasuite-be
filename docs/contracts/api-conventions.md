# HTTP API Conventions and Response Contract

## Overview and Implementation Baseline

Tài liệu này định nghĩa quy ước chuẩn cho các giao tiếp HTTP REST API của backend AquaSuite. Tất cả thông tin trong tài liệu này phản ánh chính xác mã nguồn Java đang hoạt động trong `com.aquasuite.ims.share`:
- `com.aquasuite.ims.share.dto.ApiResponse<T>`
- `com.aquasuite.ims.share.dto.FieldErrorDetail`
- `com.aquasuite.ims.share.exception.GlobalExceptionHandler`
- `com.aquasuite.ims.share.exception.AppException`
- `com.aquasuite.ims.share.exception.BaseErrorCode`
- `com.aquasuite.ims.share.exception.CommonErrorCode`

> [!IMPORTANT]
> Tài liệu này mô tả cấu trúc envelope và cơ chế xử lý lỗi hiện có. Không tự ý định nghĩa các endpoint URL hoặc mã lỗi nghiệp vụ tương lai khi chưa có Feature Spec được phê duyệt.

---

## Standard Response Structure (ApiResponse<T>)

Mọi phản hồi từ REST Controller phải được đóng gói bên trong lớp vỏ `ApiResponse<T>`.

### Response Fields Specification

| Field Name | Type | Presence | Description |
|---|---|---|---|
| `success` | `boolean` | Always present | Cờ báo hiệu trạng thái thành công (`true`) hoặc thất bại (`false`) |
| `code` | `String` | Always present | Mã định danh kết quả hoặc mã lỗi (Ví dụ: `SUCCESS`, `SYS_001`, `AUTH_001`) |
| `message` | `String` | Always present | Thông điệp mô tả kết quả trả về bằng ngôn ngữ người dùng |
| `data` | `T` | Optional (`NON_NULL`) | Dữ liệu phản hồi chính. Bị ẩn (omitted) khỏi JSON khi mang giá trị `null` do `@JsonInclude(Include.NON_NULL)` |
| `errors` | `List<FieldErrorDetail>` | Optional (`NON_EMPTY`) | Danh sách lỗi chi tiết theo từng trường dữ liệu. Bị ẩn nếu danh sách rỗng |
| `timestamp` | `Instant` | Always present | Dấu thời gian phản hồi được sinh tại thời điểm tạo response (ISO-8601 UTC) |

### Success Response Examples

#### Success Response with Data
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thành công",
  "data": {
    "id": "e2a1b3c4-1234-5678-9abc-def012345678",
    "status": "ACTIVE"
  },
  "timestamp": "2026-10-16T15:30:00.000Z"
}
```

#### Success Response with Custom Message (Null Data Omitted)
```json
{
  "success": true,
  "code": "SUCCESS",
  "message": "Thao tác thực hiện thành công",
  "timestamp": "2026-10-16T15:30:00.000Z"
}
```

---

## Error Handling and Exception Mapping

Hệ thống xử lý ngoại lệ tập trung thông qua `GlobalExceptionHandler` (`@RestControllerAdvice`). Khi có lỗi xảy ra, HTTP Status Code của Response được thiết lập tương ứng với loại ngoại lệ và `ApiResponse<Void>` chứa chi tiết lỗi được trả về trong Response Body.

### Exception Handler Mapping Table

| Exception Class | Trigger Condition | HTTP Status Code | Response Code | Error Details in `errors` |
|---|---|---|---|---|
| `AppException` | Ngoại lệ nghiệp vụ do lập trình viên chủ động ném ra, mang theo `BaseErrorCode` | Phụ thuộc `errorCode.getStatus()` | `errorCode.getCode()` | Không có (`null` / omitted) |
| `MethodArgumentNotValidException` | Lỗi xác thực dữ liệu đầu vào (`@Valid`, Bean Validation) | `400 BAD_REQUEST` | `SYS_003` (`VALIDATION_ERROR`) | Danh sách `FieldErrorDetail` (`field`, `message`) |
| `AccessDeniedException` | Người dùng không đủ quyền truy cập (Spring Security) | `403 FORBIDDEN` | `AUTH_002` (`ACCESS_DENIED`) | Không có |
| `DataIntegrityViolationException` | Trùng khóa chính/unique hoặc vi phạm ràng buộc toàn vẹn cơ sở dữ liệu | `409 CONFLICT` | `DB_001` (`DATA_INTEGRITY_VIOLATION`) | Không có |
| `Exception` | Ngoại lệ không kiểm soát (Fallback handling) | `500 INTERNAL_SERVER_ERROR` | `SYS_001` (`INTERNAL_SERVER_ERROR`) | Không có |

### Standard System Error Codes (CommonErrorCode)

Dưới đây là danh mục mã lỗi kỹ thuật chuẩn đã được hiện thực trong `CommonErrorCode`:

| Enum Name | Code | Default Message | HTTP Status |
|---|---|---|---|
| `INTERNAL_SERVER_ERROR` | `SYS_001` | Lỗi hệ thống nội bộ, vui lòng thử lại sau | `500 INTERNAL_SERVER_ERROR` |
| `INVALID_REQUEST_PARAM` | `SYS_002` | Tham số đầu vào không hợp lệ | `400 BAD_REQUEST` |
| `VALIDATION_ERROR` | `SYS_003` | Dữ liệu đầu vào không hợp lệ | `400 BAD_REQUEST` |
| `RESOURCE_NOT_FOUND` | `SYS_004` | Không tìm thấy tài nguyên yêu cầu | `404 NOT_FOUND` |
| `UNAUTHORIZED` | `AUTH_001` | Chưa xác thực hoặc phiên đăng nhập đã hết hạn | `401 UNAUTHORIZED` |
| `ACCESS_DENIED` | `AUTH_002` | Bạn không có quyền thực hiện thao tác này | `403 FORBIDDEN` |
| `DATA_INTEGRITY_VIOLATION` | `DB_001` | Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc toàn vẹn | `409 CONFLICT` |

### Error Response Examples

#### Business Exception Response (AppException)
```json
{
  "success": false,
  "code": "SYS_004",
  "message": "Không tìm thấy tài nguyên yêu cầu",
  "timestamp": "2026-10-16T15:30:00.000Z"
}
```

#### Validation Error Response (MethodArgumentNotValidException)
```json
{
  "success": false,
  "code": "SYS_003",
  "message": "Dữ liệu đầu vào không hợp lệ",
  "errors": [
    {
      "field": "email",
      "message": "Email không đúng định dạng"
    },
    {
      "field": "phone",
      "message": "Số điện thoại không được để trống"
    }
  ],
  "timestamp": "2026-10-16T15:30:00.000Z"
}
```

#### Data Integrity Violation Response (DataIntegrityViolationException)
```json
{
  "success": false,
  "code": "DB_001",
  "message": "Dữ liệu bị trùng lặp hoặc vi phạm ràng buộc toàn vẹn",
  "timestamp": "2026-10-16T15:30:00.000Z"
}
```

---

## Guidelines for Future Endpoint Documentation

Khi các feature được thiết kế chi tiết trong các Feature Spec, tài liệu đặc tả endpoint phải tuân theo các nguyên tắc sau:
1. **URI Path Naming**: Sử dụng danh từ số nhiều, chữ thường, phân cách bằng dấu gạch ngang (kebab-case), ví dụ `/api/v1/sales-orders`.
2. **HTTP Methods**: Sử dụng đúng ngữ nghĩa (`GET` cho truy vấn, `POST` cho tạo mới, `PUT` cho cập nhật toàn bộ, `PATCH` cho cập nhật từng phần, `DELETE` cho xóa).
3. **Payload Mapping**: Mọi request body phải có validation annotation (`@NotNull`, `@NotBlank`, `@Size`, v.v.). Response trả về bắt buộc gói trong `ApiResponse<T>`.
4. **Business Error Codes**: Khi feature cần mã lỗi nghiệp vụ riêng (ví dụ `SALES_001`, `INV_002`), phải định nghĩa enum kế thừa `BaseErrorCode` và chỉ định rõ HTTP Status tương ứng.
