# AquaSuite Backend Documentation Index

## Purpose and Document Hierarchy

Tài liệu này là bản đồ chỉ mục cho toàn bộ hệ thống tài liệu phát triển backend của dự án AquaSuite. Để giải quyết các mâu thuẫn thông tin có thể phát sinh trong quá trình phát triển, thứ tự ưu tiên nguồn sự thật (Source of Truth) được quy định như sau:

1. **Mã nguồn đang hoạt động (Running Code)**: Là bằng chứng xác thực nhất về hiện trạng hệ thống (phiên bản runtime, cấu trúc package hiện tại, các exception handler và response format đang vận hành).
2. **Feature Spec và Hợp đồng đã phê duyệt (Approved Specs & Contracts)**: Là mục tiêu nghiệp vụ và tiêu chuẩn kỹ thuật có thẩm quyền cho tính năng đang được triển khai.
3. **Tài liệu Kiến trúc tổng thể (Architecture Documentation)**: Xác lập các quy tắc bất biến, ranh giới module, và định hướng kiến trúc mục tiêu lâu dài.

---

## Documentation Directory Map

Hệ thống tài liệu backend bao gồm các tài liệu chuyên đề sau:

### 1. Architecture and Design
- [`architecture/Architecture-Aquasuite.md`](architecture/Architecture-Aquasuite.md):
  - Phân định giữa mã nguồn hiện trạng (`com.aquasuite.ims`) và kiến trúc đích (`com.aquasuite.be`).
  - Cấu trúc Package-by-Feature (Modular Monolith), quy tắc đóng gói `api` và `internal`.
  - Bảng ánh xạ 11 miền nghiệp vụ ứng viên gắn với 14 mã tính năng (FE-01 đến FE-14).
  - Ma trận giao tiếp đồng bộ và sự kiện phân tách (decoupled events), loại bỏ vòng lặp phụ thuộc `sales <-> crm`.
- [`architecture/module-contracts.md`](architecture/module-contracts.md):
  - Tiêu chuẩn thiết kế Java API đồng bộ (`SampleApi`) và Domain Events (`record`).
  - Các trường bắt buộc khi đặc tả hợp đồng liên module.
  - Hướng dẫn hiện thực `@TransactionalEventListener` an toàn với transaction mới và tính idempotent.
  - Các cổng quyết định nhất quán còn mở (`TBD`) về tồn kho, thanh toán, giao hàng và bảo hành.

### 2. Contracts and Conventions
- [`contracts/api-conventions.md`](contracts/api-conventions.md):
  - Quy ước cấu trúc phản hồi HTTP chuẩn hóa qua `ApiResponse<T>` và `FieldErrorDetail`.
  - Cơ chế bắt lỗi tập trung trong `GlobalExceptionHandler` (`AppException`, validation, access denied, DB integrity).
  - Danh mục mã lỗi hệ thống chuẩn `CommonErrorCode` (`SYS_001` đến `SYS_004`, `AUTH_001`, `AUTH_002`, `DB_001`).
  - Quy chuẩn đặt tên URI và phương thức HTTP cho các Feature Spec tương lai.

### 3. Specifications and Templates
- [`templates/feature-spec.md`](templates/feature-spec.md):
  - Mẫu tài liệu đặc tả tính năng chuẩn hóa.
  - Phân loại độ sâu tài liệu theo mức độ rủi ro (Risk-Based Depth).
  - Quy định đầy đủ về Metadata, Actor, Business Rules, Data Model, API/Event Contracts, Concurrency và Ma trận AC-to-Test.

### 4. Development Workflow
- [`workflow/feature-delivery.md`](workflow/feature-delivery.md):
  - Quy trình phát triển 4 giai đoạn: Spec Review -> Plan Review -> Implementation & Handoff -> Final Gate.
  - Quy định thẩm quyền triển khai, cổng bàn giao thực thi (Implementation Handoff Gate) và quản lý thay đổi hợp đồng.
  - Quy tắc an toàn Git: Nghiêm cấm Agent tự ý commit, push, reset hoặc clean mã nguồn.

### 5. Migrations
- [`migrations/spring-boot-3.5.md`](migrations/spring-boot-3.5.md):
  - Ghi nhận chi tiết đợt di chuyển baseline công nghệ từ Spring Boot 4.1.1 sang Spring Boot 3.5.16 (`FEATURE-004`).
  - Danh mục dependency được BOM quản lý, các điều chỉnh tương thích và kết quả kiểm chứng baseline.

---

## Reading Order for Developers and Agents

Khi tiếp cận dự án để nghiên cứu hoặc triển khai công việc mới, hãy đọc theo trình tự:
1. Đọc [`../AGENTS.md`](../AGENTS.md) tại thư mục gốc để nắm các chỉ thị an toàn và nguyên tắc cốt lõi.
2. Đọc [`architecture/Architecture-Aquasuite.md`](architecture/Architecture-Aquasuite.md) để nắm rõ ranh giới module và trạng thái hệ thống.
3. Đọc [`contracts/api-conventions.md`](contracts/api-conventions.md) và [`architecture/module-contracts.md`](architecture/module-contracts.md) trước khi thiết kế bất kỳ API hoặc Event nào.
4. Đọc [`workflow/feature-delivery.md`](workflow/feature-delivery.md) để tuân thủ đúng các cổng phê duyệt trước khi lập trình.
