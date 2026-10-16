# Feature Delivery Workflow and Review Gates

## Purpose and Scope

Tài liệu này xác lập quy trình chuẩn từ khâu đặc tả yêu cầu đến khi bàn giao tính năng trong kho lưu trữ AquaSuite backend. Mục tiêu là đảm bảo chất lượng kỹ thuật, tính toàn vẹn của kiến trúc và sự phối hợp nhịp nhàng giữa kỹ sư con người và AI Agent.

---

## Delivery Stages and Quality Gates

Quy trình triển khai mỗi tính năng phải trải qua 4 giai đoạn tuần tự với các cổng kiểm duyệt (Gates) tương ứng:

```text
[Giai đoạn 1: Spec Creation & Review]
         │
         ▼ (Gate 1: SPEC REVIEW APPROVED)
[Giai đoạn 2: Implementation Plan & Review]
         │
         ▼ (Gate 2: PLAN REVIEW APPROVED)
[Giai đoạn 3: Implementation & Local Verification]
         │
         ▼ (Handoff Gate: IMPLEMENTATION_STATUS: READY_FOR_REVIEW)
         ▼ (Gate 3: CODE REVIEW APPROVED)
[Giai đoạn 4: Final Gate & Integration]
```

### Stage 1: Specification Creation and Review
1. **Lập đặc tả**: Tác giả (Kỹ sư hoặc AI) khởi tạo Feature Spec theo mẫu `docs/templates/feature-spec.md`.
2. **Nội dung trọng yếu**: Phải xác định rõ Owning Module, ranh giới package, mô hình dữ liệu, hợp đồng API/Event, các tiêu chí nghiệm thu (AC) và ma trận kiểm thử (AC-to-Test).
3. **Cổng kiểm duyệt (Spec Review Gate)**:
   - Đánh giá tính khả thi, sự phù hợp với ranh giới kiến trúc trong `docs/architecture/Architecture-Aquasuite.md`.
   - Kết quả: `APPROVED` hoặc `CHANGES_REQUESTED`. Chỉ khi được phê duyệt mới được chuyển sang Giai đoạn 2.

### Stage 2: Implementation Planning and Review
1. **Lập kế hoạch thực thi**: Khởi tạo kế hoạch chi tiết (Active Plan) xác định rõ:
   - `PLAN_ID`, `PLAN_VERSION`, `SCOPE`.
   - Repository Map và ranh giới Git root.
   - Danh sách file cụ thể sẽ thay đổi hoặc tạo mới.
   - Các lane triển khai tuần tự hoặc song song (phân định file ownership để tránh xung đột).
2. **Cổng kiểm duyệt kế hoạch (Plan Review Gate)**:
   - Kế hoạch phải được kiểm tra độc lập và cấp trạng thái `PLAN_REVIEW: APPROVED`.
   - Mọi thay đổi nội dung của plan sau khi duyệt sẽ làm tăng `PLAN_VERSION` và khiến review cũ mất hiệu lực (stale).

### Stage 3: Implementation, Verification, and Handoff
1. **Thẩm quyền triển khai (Authorization Gate)**:
   - Agent hoặc kỹ sư chỉ được phép sửa mã nguồn khi có đúng một active plan kèm phê duyệt `APPROVED` còn hiệu lực và mã băm SHA-256 khớp tuyệt đối.
2. **Quy tắc thực thi (Implementation Rules)**:
   - Chỉ chỉnh sửa trong phạm vi file đã đăng ký trong plan.
   - Tuân thủ coding guardrails và package visibility.
   - Chạy đầy đủ các bước kiểm thử tự động, build và verification được nêu trong plan.
3. **Cổng bàn giao thực thi (Implementation Handoff Gate)**:
   - Sau khi hoàn thành các lane và bước kiểm tra thực tế, Agent/kỹ sư triển khai bắt buộc phải xuất artifact bàn giao tổng hợp (Handoff Artifact) lưu tại `<PROJECT_ROOT>/.agent/handoff.md` (hoặc `.agent/handoffs/<PLAN_ID>.md`).
   - Yêu cầu cấu trúc bắt buộc:
     - `IMPLEMENTATION_STATUS: READY_FOR_REVIEW`
     - `PLAN_ID: <PLAN_ID>`
     - `PLAN_VERSION: <PLAN_VERSION>`
     - `## Lanes Completed` (xác nhận hoàn tất toàn bộ các lane tuần tự/song song theo plan)
     - `## Files Changed` (ghi rõ `Repository` ID và `Relative Path` cho từng file)
     - `## Implementation Summary`
     - `## Tests Added / Updated`
     - `## Verification Performed` (`PASS`, `FAIL`, `NOT_RUN`, `NOT_APPLICABLE` có thể tái lập)
     - `## Contract Verification` (đối chiếu API/data contract)
     - `## Deviations From Approved Plan` (`None` nếu tuân thủ tuyệt đối)
     - `## Known Limitations`
   - Chỉ khi artifact handoff này tồn tại trên đĩa và đầy đủ thì cổng Code Review mới được phép kích hoạt. Thiếu handoff, Code Review sẽ bị chặn ngay tại precondition (`CODE_REVIEW: BLOCKED`).
4. **Cổng duyệt mã nguồn (Code Review Gate)**:
   - Kiểm tra mã nguồn thực tế so với kế hoạch đã duyệt và tiêu chuẩn kiến trúc.
   - Đánh giá kết quả kiểm thử thực tế (`PASS`, `FAIL`, `NOT_RUN`, `NOT_APPLICABLE`).
   - Nếu có phát hiện lỗi: Đưa ra `CHANGES_REQUESTED`. Đội triển khai phải sửa chữa và bàn giao lại.

### Stage 4: Final Gate and Integration
- Xác nhận toàn bộ tiêu chí AC đã đạt và kiểm thử tự động vượt qua.
- Đóng gói tài liệu bàn giao và hoàn tất tính năng.

---

## Contract and Specification Change Management

Khi tính năng đã bước vào giai đoạn triển khai hoặc đã hoàn thành mà phát sinh yêu cầu thay đổi:

1. **Thay đổi Hợp đồng API / Event**:
   - Nếu thay đổi ảnh hưởng đến Consumer ở feature khác hoặc Frontend: **Bắt buộc dừng việc triển khai ngay lập tức**.
   - Phải báo cáo leo thang (`ESCALATION: PLAN_REVISION_REQUIRED`).
   - Cập nhật lại Feature Spec và Active Plan, tăng số hiệu phiên bản (`VERSION`), và thực hiện kiểm duyệt lại từ Gate 1 / Gate 2.
2. **Thay đổi Logic Nội bộ trong Package `internal`**:
   - Các điều chỉnh hiện thực không làm thay đổi public contract, schema hay ranh giới module có thể được điều chỉnh trong cùng phiên bản kế hoạch nếu được Code Review chấp thuận.

---

## Git Safety and Automation Boundaries

Để bảo vệ lịch sử và sự ổn định của repository:

> [!CAUTION]
> 1. **Cấm tự động Git Mutation**: AI Agent tuyệt đối không tự ý thực hiện các lệnh: `git commit`, `git push`, `git reset`, `git clean`, `git rebase`, hoặc `git checkout --force`.
> 2. **Bảo toàn thay đổi chưa commit**: Khi bắt đầu bất kỳ phiên làm việc nào, Agent phải kiểm tra `git status` và bảo vệ toàn bộ các file chưa commit của lập trình viên.
> 3. **Quyền sở hữu Commit**: Mọi thao tác commit và push lên remote repository phải do kỹ sư con người trực tiếp rà soát và thực thi.
