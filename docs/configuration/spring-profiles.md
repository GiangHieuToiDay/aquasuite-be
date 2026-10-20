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
| `application.yml` | Cấu hình chung cho mọi môi trường | Tên app/marker, server, JDBC qua `SPRING_DATASOURCE_*`, JPA batching, Redis và SMTP | URL JDBC local mặc định `jdbc:postgresql://localhost:5432/aquasuite`; username/password vẫn lấy từ môi trường, không fallback credential. Không kích hoạt profile; JPA mặc định `validate`, cần triển khai schema trước khi chạy. |
| `application-dev.yml` | Ranh giới môi trường Development | `aquasuite.config.environment: dev` | Không hardcode credential cục bộ; không tự ý thay đổi runtime/logging defaults. |
| `application-test.yml` | Ranh giới môi trường Testing / CI | `aquasuite.config.environment: test` | Không giả định có database bên ngoài sẵn có; cấu hình kiểm thử cần độc lập và tự cô lập. |
| `application-prod.yml` | Ranh giới môi trường Production | Marker `prod`, JDBC qua `SPRING_DATASOURCE_*`, SMTP qua `MAIL_USERNAME`/`MAIL_PASSWORD` | Không fallback credential; cần cung cấp các biến môi trường tương ứng. |

---

## 3. Danh mục Biến Môi trường (Environment Variables)

Hệ thống hỗ trợ nạp cấu hình từ biến môi trường theo quy tắc Externalized Configuration của Spring Boot:

| Tên Biến Môi Trường | Phân loại | Mục đích | Ví dụ Giá trị (Placeholder) |
|---|---|---|---|
| `SPRING_PROFILES_ACTIVE` | Non-secret | Kích hoạt profile tương ứng (`dev`, `test`, `prod`) | `dev` |
| `SPRING_DATASOURCE_URL` | Non-secret | JDBC URL PostgreSQL; common mặc định local, profile prod bắt buộc cấp từ môi trường | `<replace-with-local-or-managed-jdbc-url>` |
| `SPRING_DATASOURCE_USERNAME` | Non-secret | Tên người dùng kết nối cơ sở dữ liệu | `<replace-with-username>` |
| `SPRING_DATASOURCE_PASSWORD` | **Secret** | Mật khẩu truy cập cơ sở dữ liệu | `<replace-with-secret>` |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | Non-secret | Quản lý schema, mặc định `validate`; chạy migration trước khởi động | `validate` |
| `MAIL_HOST` / `MAIL_PORT` | Non-secret | SMTP host/port; mặc định smtp.gmail.com/587 | `<replace-with-smtp-host>` / `587` |
| `MAIL_USERNAME` | Cấu hình tài khoản | Tài khoản SMTP từ môi trường | `<replace-with-smtp-username>` |
| `MAIL_PASSWORD` | **Secret** | Mật khẩu/secret SMTP, không có fallback | `<replace-with-smtp-secret>` |

> [!WARNING]
> - Spring Boot **không tự động đọc** file `.env`. Nhà phát triển cần thiết lập biến môi trường qua command line, IDE Run Configuration hoặc Secret Manager.
> - Tuyệt đối không commit giá trị secret thật vào repository hoặc file `.env.example`.

---

## 4. Hướng dẫn Khởi chạy trên Windows PowerShell

### 4.1. Môi trường Phát triển (Development)

Thiết lập profile `dev` và khởi chạy ứng dụng bằng Maven Wrapper:

```powershell
$env:SPRING_PROFILES_ACTIVE = 'dev'
# Cấp SPRING_DATASOURCE_*, MAIL_USERNAME và MAIL_PASSWORD qua môi trường.
# Triển khai schema/migration trước; mặc định JPA chỉ validate, không tạo lại bảng.
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

## Flyway

Flyway creates/upgrades schema before Hibernate `validate`. See [migration setup and baseline procedure](../migrations/flyway.md). Defaults: `baseline-on-migrate=false`, `clean-disabled=true`, `validate-on-migrate=true`. Existing databases require schema review and an explicit one-time baseline.


## Authentication merge

The merged runtime requires JWT_SECRET (Base64, at least 64 bytes decoded) and PostgreSQL/Redis configuration from environment. Login and refresh POST routes are the only authentication allowlist; all other APIs require an access JWT, including combo browsing/evaluation and password forgot/reset. Refresh tokens cannot authorize those APIs. Development account seeding is disabled by default: opt in with AQUASUITE_DEV_SEED_ENABLED=true and supply AQUASUITE_DEV_OWNER_PASSWORD through the environment. No hardcoded default account or user-ID fallback remains. SMTP credentials remain environment-only. Test-only signing values are isolated in test configuration; never reuse them for deployment.
