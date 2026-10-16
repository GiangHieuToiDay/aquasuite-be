# Agent Guidelines - AquaSuite Backend

## Repository Context and Boundaries

- **Project Root**: `D:\DO_AN\Aquasuite\aquasuite-be`
- **Git Scope**: Single-repo (`aquasuite-be`). Thư mục sibling `aquasuite-fe` thuộc một Git repository độc lập và nằm ngoài phạm vi của các tác vụ tại repository này.
- **Workflow State**: Trạng thái workflow được lưu trong thư mục `.agent/` (local-only, được ignore bởi Git).

---

## Documentation and Reading Sequence

Trước khi phân tích, lập kế hoạch hoặc thay đổi mã nguồn, Agent bắt buộc phải đọc tài liệu theo trình tự sau:

1. **Bản đồ tài liệu và Thứ tự ưu tiên**: Xem [`docs/README.md`](docs/README.md) để xác định nguồn sự thật và cấu trúc tài liệu.
2. **Kiến trúc và Ranh giới Module**: Xem [`docs/architecture/Architecture-Aquasuite.md`](docs/architecture/Architecture-Aquasuite.md) để nắm rõ kiến trúc module hóa chuẩn dưới `com.aquasuite.ims.module`, cũng như ranh giới giữa public contract (`api/`) và private implementation (`internal/`).
3. **Quy ước Hợp đồng và Giao tiếp**:
   - Đối với HTTP API: Xem [`docs/contracts/api-conventions.md`](docs/contracts/api-conventions.md).
   - Đối với Java API và Domain Events: Xem [`docs/architecture/module-contracts.md`](docs/architecture/module-contracts.md).
4. **Quy trình Phát triển và Cổng Kiểm duyệt**: Xem [`docs/workflow/feature-delivery.md`](docs/workflow/feature-delivery.md).
5. **Mẫu Đặc tả Tính năng**: Khi soạn thảo đặc tả tính năng mới, tuân thủ [`docs/templates/feature-spec.md`](docs/templates/feature-spec.md).

---

## Invariant Rules for Autonomous Agents

Mọi Agent hoạt động trong repository này phải tuân thủ nghiêm ngặt các quy tắc bất biến sau:

1. **Kiến trúc Chuẩn và Runtime Baseline**:
   - Base package chuẩn duy nhất của hệ thống là `com.aquasuite.ims`.
   - Cấu trúc module chuẩn được đặt trực tiếp dưới `com.aquasuite.ims.module.<module_name>`.
   - Gói `com.aquasuite.ims.share` chỉ dành riêng cho technical shared infrastructure độc lập với nghiệp vụ (không chứa business logic, domain entity hoặc repository của module).
   - Hệ quản trị cơ sở dữ liệu chuẩn là PostgreSQL.
   - Hệ thống chạy trên **Java 21, Spring Boot 3.5.16** (quản lý Spring Security 6.5.x qua BOM, direct dependency `spring-security-core`). Không tự ý thực hiện migration package nếu không có kế hoạch được phê duyệt riêng.
2. **Quy tắc Đóng gói Module (Package Encapsulation)**:
   - Mỗi module gồm hai phân vùng rõ rệt: `api/` (public contract) và `internal/` (private implementation).
   - Khi giao tiếp liên module, chỉ được chia sẻ qua gói `api/`. Tuyệt đối cấm import từ `internal/` của module khác.
   - Tuyệt đối không tạo liên kết JPA Entity (`@ManyToOne`, `@OneToMany`) chéo module; liên kết dữ liệu chéo module trong Java model sử dụng scalar ID.
3. **Xác minh Trạng thái Git Trước khi Làm việc**:
   - Luôn kiểm tra `git status` trước khi thực hiện thay đổi.
   - Bảo toàn tuyệt đối mọi thay đổi chưa commit (uncommitted changes) sẵn có của người dùng.
4. **An toàn Git (Git Safety Boundary)**:
   - Tuyệt đối không tự ý thực thi các lệnh làm thay đổi lịch sử Git: `git commit`, `git push`, `git reset`, `git clean`, `git rebase`, hoặc `git checkout --force`.
5. **Báo cáo Xung đột (Conflict Reporting)**:
   - Nếu phát hiện mâu thuẫn giữa tài liệu đặc tả (Spec) và mã nguồn đang chạy, hoặc giữa các tài liệu kiến trúc với nhau: Dừng thao tác, không đưa ra giả định, và báo cáo ngay cho người dùng để làm rõ.
6. **Bảo mật Thông tin và Quản lý Secret (Secret Security Policy)**:
   - Tuyệt đối cấm commit hoặc lưu trữ thông tin nhạy cảm thật (password, token, JWT secret, API key, OAuth client secret, private key, keystore credential) vào mã nguồn, file cấu hình, tài liệu hoặc workflow artifacts.
   - Mọi credential và secret phải được nạp thông qua biến môi trường (environment variables) hoặc secret management system khi triển khai.
   - Luôn kiểm tra kỹ diff trước khi thực hiện các thao tác Git để đảm bảo không rò rỉ credential.
   - Khi phát hiện rò rỉ credential trong repository hoặc lịch sử Git: Dừng ngay lập tức thao tác, thông báo cho chủ sở hữu repository để thực hiện thu hồi/đổi mới (revoke/rotate) credential ngay lập tức, và xử lý Git history bằng quy trình do con người phê duyệt; Agent tuyệt đối không tự ý rewrite lịch sử Git.

---

## Repository Governance and Tracking Policy

1. **Theo dõi Quy chuẩn Quản trị (Governance File Tracking)**:
   - [`AGENTS.md`](AGENTS.md) là tài liệu governance định nghĩa các quy chuẩn cốt lõi của repository và cần được theo dõi bởi Git (tracked).
2. **Quản lý Thư mục Workflow Artifacts (`.agent/`)**:
   - Thư mục `.agent/` là workflow state local-only của repository, được quản lý trên máy cục bộ và được ignore bởi Git (`.gitignore`).
   - Mặc dù mang tính chất local-only, Agent vẫn phải bảo đảm tính toàn vẹn của audit trail: tuân thủ nghiêm ngặt tính duy nhất của active plan (`plans/active`), kết quả đánh giá (`reviews`), biên bản bàn giao (`handoffs`) và tài liệu lưu trữ (`archive`).
   - Tuyệt đối bảo toàn và không sửa đổi, xóa hoặc tái cấu trúc các artifact đã archive hoặc cancelled (ví dụ `FEATURE-001`, `FEATURE-003`), và không tự ý sao chép artifact sang project khác.
   - Mọi thao tác stage hoặc commit [`AGENTS.md`](AGENTS.md) thuộc quyền quyết định độc quyền của con người (human-only decision). Agent không tự ý stage hoặc commit tệp này.
