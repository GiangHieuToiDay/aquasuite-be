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
  - Cấu trúc kiến trúc module hóa chuẩn đặt trực tiếp dưới `com.aquasuite.ims.module` với base package duy nhất `com.aquasuite.ims`.
  - Cấu trúc Modular Monolith chuẩn hóa, quy tắc đóng gói giữa hợp đồng công khai (`api`) và triển khai nội bộ (`internal`).
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
- [`specs/SCRUM14-UC13-product-combos.md`](specs/SCRUM14-UC13-product-combos.md):
  - Spec UC-13 revision `0.11.0`: combo Phase 2 và tích hợp authentication mới; User/actor UUID, JWT bắt buộc cho các API ngoài login/refresh. Một kho, option cùng giá; business Audit và media vẫn deferred.
  - Package triển khai đã được người dùng chốt cho SCRUM14: `com.aquasuite.ims.module.catalog`; public contract mới trong `api/`.
  - Core dùng MapStruct, gộp controller và application service CRUD/SKU lookup; pricing/assembler giữ nhiệm vụ riêng, controller/service/DTO có JavaDoc tác dụng/luồng. HTTP contract giữ nguyên. Clean verify sau gộp ngày 2026-10-20 đạt 47 unit + 38 integration = 85 tests, 0 failures/errors/skipped; sẵn sàng code review, chưa Final Gate. Public/media/tồn/options/Sales thuộc phase sau; review/hash `0.8.1` không áp dụng.
  - [Plan 0.11.0 và báo cáo tiến độ](../.agent/plans/SCRUM14-UC13.md): ánh xạ spec → công việc → bằng chứng/việc còn lại, không tuyên bố hoàn tất toàn UC.
  - [Implementation handoff hiện hành](../.agent/handoffs/SCRUM14-UC13-phase1-verification.md), [code guide Phase 1](SCRUM14-UC13-phase1-code-guide.md), [migration runbook](migrations/flyway.md). Thư mục handoff chỉ giữ bản mới nhất của UC-13; các bản cũ đã được bỏ theo yêu cầu người dùng ngày 2026-10-19.
  - Hai review 0.8.1 lỗi thời đã được xóa theo yêu cầu người dùng ngày 2026-10-19; chưa có review mới. Kết quả test và log tái lập nằm trong handoff hiện hành.
- [`templates/feature-spec.md`](templates/feature-spec.md):
  - Mẫu tài liệu đặc tả tính năng chuẩn hóa.
  - Phân loại độ sâu tài liệu theo mức độ rủi ro (Risk-Based Depth).
  - Quy định đầy đủ về Metadata, Actor, Business Rules, Data Model, API/Event Contracts, Concurrency và Ma trận AC-to-Test.

### 4. Development Workflow
- [`workflow/feature-delivery.md`](workflow/feature-delivery.md):
  - Quy trình quản trị phân tầng: Tiền điều kiện cấp dự án (Spec Review) trước khi lập kế hoạch, tiếp nối bởi chuỗi cổng kiểm duyệt của Global Workflow v2 (Plan Review -> Implementation & Handoff -> Code Review -> Final Gate).
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

- [Flyway database migration and existing database onboarding](migrations/flyway.md).

Flyway-only revision: plan 0.9.6 / spec 0.9.1; clean verify 88 tests PASS (47 + 41), 2026-10-20 13:36:48 +07.


Current Phase 2 revision: spec/plan 0.10.0, implementation authorized by user. Same-price options; one warehouse; Auth/Audit/media deferred. Adds public browsing/evaluations, publication lifecycle and dates. Clean verify PASS: 56 unit + 55 integration = 111 tests, zero failures/errors/skipped (2026-10-20 15:05:17 +07). Independent review/Final Gate pending. Phase 1 results above are historical. See [.agent/plans/SCRUM14-UC13.md](../.agent/plans/SCRUM14-UC13.md).

Phase 2 evidence: [implementation handoff](../.agent/handoffs/SCRUM14-UC13-phase2-verification.md).


Authentication integration (spec/plan 0.11.0): merge source `origin/SCRUM-1-user-login-and-authentication` at `e365cad`. User now requires JWT on all non-login/refresh APIs, including catalog storefront/admin/evaluations and inventory. User IDs and actor references use UUID; mock identity and the old credential initializer are removed. Auth/audit identity types are integrated, while new business audit writes and media remain deferred. V1/V2/V3 remain unchanged; V4 integrates IAM and V5 preserves password recovery. Verification PASS: 251 tests (186 Surefire + 65 Failsafe), zero failures/errors/skipped, 2026-10-20 17:56:28 +07. Earlier no-token descriptions/test counts are historical.

Authentication merge evidence: [handoff](../.agent/handoffs/authentication-merge-verification.md). Git merge is resolved but uncommitted; independent review remains pending.
