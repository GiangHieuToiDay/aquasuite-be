# Spring Profiles Configuration Guide - AquaSuite Backend

## 1. Tổng quan Kiến trúc Cấu hình

Hệ thống cấu hình của AquaSuite Backend được chuẩn hóa thành 4 tập tin Spring YAML độc lập trong `src/main/resources`:
- `application.yml`: Chứa cấu hình dùng chung (common configuration) cho toàn bộ ứng dụng. Không kích hoạt bất kỳ profile mặc định nào.
- `application-dev.yml`: Phục vụ môi trường phát triển cục bộ (Local Development).
- `application-test.yml`: Phục vụ môi trường kiểm thử tự động (Automated Testing / CI).
- `application-prod.yml`: Phục vụ môi trường vận hành thực tế (Production).

> [!NOTE]
> Thuộc tính `aquasuite.config.environment` là một **diagnostic marker** (non-secret metadata) được thiết kế riêng nhằm kiểm tra tính đúng đắn của việc nạp cấu hình và độ ưu tiên (precedence) qua Spring Boot ConfigData. Marker này **tuyệt đối không được sử dụng** để rẽ nhánh logic nghiệp vụ (business logic) hoặc làm public configuration contract.

---

## 2. Configuration Ownership Matrix

| File Cấu hình | Vai trò & Trách nhiệm | Nội dung Cấu hình Hiện tại | Lưu ý Bảo mật & Vận hành |
|---|---|---|---|
| `application.yml` | Cấu hình chung cho mọi môi trường | `spring.application.name: aquasuite-be`<br>`aquasuite.config.environment: default` | Không khai báo `spring.profiles.active`, datasource URL hoặc secret nào. |
| `application-dev.yml` | Ranh giới môi trường Development | `aquasuite.config.environment: dev` | Không hardcode credential cục bộ; không tự ý thay đổi runtime/logging defaults. |
| `application-test.yml` | Ranh giới môi trường Testing / CI | `aquasuite.config.environment: test` | Không giả định có database bên ngoài sẵn có; cấu hình kiểm thử cần độc lập và tự cô lập. |
| `application-prod.yml` | Ranh giới môi trường Production | `aquasuite.config.environment: prod` | Tuyệt đối cấm fallback hoặc default credential rỗng; mọi secret phải nạp từ môi trường chạy. |

---

## 3. Danh mục Biến Môi trường (Environment Variables)

Hệ thống hỗ trợ nạp cấu hình từ biến môi trường theo quy tắc Externalized Configuration của Spring Boot:

| Tên Biến Môi Trường | Phân loại | Mục đích | Ví dụ Giá trị (Placeholder) |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Non-secret | Kích hoạt profile tương ứng (`dev`, `test`, `prod`) | `dev` |
| `SPRING_DATASOURCE_URL` | Non-secret | JDBC URL kết nối cơ sở dữ liệu PostgreSQL | `<replace-with-local-or-managed-jdbc-url>` |
| `SPRING_DATASOURCE_USERNAME` | Non-secret | Tên người dùng kết nối cơ sở dữ liệu | `<replace-with-username>` |
| `SPRING_DATASOURCE_PASSWORD` | **Secret** | Mật khẩu truy cập cơ sở dữ liệu | `<replace-with-secret>` |

> [!WARNING]
> - Spring Boot **không tự động đọc** file `.env`. Nhà phát triển cần thiết lập biến môi trường qua command line, IDE Run Configuration hoặc Secret Manager.
> - Tuyệt đối không commit giá trị secret thật vào repository hoặc file `.env.example`.

---

## 4. Hướng dẫn Khởi chạy trên Windows PowerShell

### 4.1. Môi trường Phát triển (Development)

Thiết lập profile `dev` và khởi chạy ứng dụng bằng Maven Wrapper:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
# Thiết lập các biến SPRING_DATASOURCE_* trong shell nếu cần kết nối database
.\mvnw.cmd spring-boot:run
```

Sau khi hoàn tất phiên làm việc, dọn dẹp các biến môi trường trong PowerShell session:
```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
```

### 4.2. Môi trường Kiểm thử (Testing)

Chạy kiểm thử với profile `test`:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'test'
.\mvnw.cmd test
```

Dọn dẹp môi trường sau khi test:
```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
```

### 4.3. Đóng gói và Khởi chạy Production

Đóng gói ứng dụng thành file JAR (bỏ qua unit tests nếu môi trường build không có sẵn database):

```powershell
.\mvnw.cmd "-DskipTests" package
```

Khởi chạy ứng dụng production với các biến môi trường bắt buộc (cung cấp qua Secret Manager hoặc biến môi trường container/hệ thống):

```powershell
$env:SPRING_PROFILES_ACTIVE = 'prod'
# Cung cấp biến kết nối SPRING_DATASOURCE_* từ Secret Manager hoặc môi trường vận hành
java -jar .\target\aquasuite-0.0.1-SNAPSHOT.jar
```

Dọn dẹp biến môi trường sau khi tắt:
```powershell
Remove-Item Env:SPRING_PROFILES_ACTIVE -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_URL -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_USERNAME -ErrorAction SilentlyContinue
Remove-Item Env:SPRING_DATASOURCE_PASSWORD -ErrorAction SilentlyContinue
```

---

## 5. Nguyên tắc Bảo mật và Vận hành

1. **Không in hoặc ghi log Secret**: Tránh sử dụng `Get-ChildItem Env:` hoặc xuất dump biến môi trường ra log / console terminal.
2. **Kiểm tra trước khi Commit**: Luôn chạy `git status` và `git diff` để đảm bảo không vô tình đưa thông tin nhạy cảm vào Git index.
3. **Phản hồi sự cố rò rỉ**: Nếu phát hiện secret bị lộ, ngay lập tức thu hồi/đổi mới credential trên hệ thống tương ứng và báo cáo cho người quản trị để xử lý lịch sử Git.
