# Feature Delivery Workflow and Review Gates

## Purpose and Scope

Tài liệu này xác lập quy trình chuẩn từ khâu đặc tả yêu cầu đến khi bàn giao tính năng trong kho lưu trữ AquaSuite backend. Mục tiêu là đảm bảo chất lượng kỹ thuật, tính toàn vẹn của kiến trúc và sự phối hợp nhịp nhàng giữa kỹ sư con người và AI Agent.

---

## Delivery Stages and Quality Gates

Quy trình triển khai mỗi tính năng phân tách rõ ràng giữa tiền điều kiện cấp dự án (project-level precondition) và chuỗi cổng kiểm soát chất lượng của Global Workflow v2:

```text
[Tiền điều kiện dự án: Lập Feature Spec]
         │
         ▼ (Thẩm quyền dự án: Spec Review Được Phê Duyệt)
[Khởi tạo Kế hoạch: $plan-feature]
         │
         ▼ (Cổng kiểm duyệt: PLAN_REVIEW: APPROVED)
[Thực thi Kỹ thuật & Kiểm thử: ANTI_DEV]
         │
         ▼ (Cổng bàn giao: IMPLEMENTATION_STATUS: READY_FOR_REVIEW)
         ▼ (Cổng duyệt mã: CODE_REVIEW: APPROVED)
         │
         ▼ (Cổng nghiệm thu cuối: FINAL_GATE: PASS)
```

### Stage 1: Specification Creation and Spec Review (Project Precondition)
1. **Lập đặc tả**: Tác giả (Kỹ sư hoặc AI) khởi tạo Feature Spec theo mẫu `docs/templates/feature-spec.md`.
2. **Nội dung trọng yếu**: Phải xác định rõ Owning Module, ranh giới package, mô hình dữ liệu, hợp đồng API/Event, các tiêu chí nghiệm thu (AC) và ma trận kiểm thử (AC-to-Test).
3. **Tiền điều kiện cấp dự án (project-level precondition)**:
   - Đánh giá tính khả thi và sự phù hợp với ranh giới kiến trúc trong `docs/architecture/Architecture-Aquasuite.md`.
   - Thẩm quyền đánh giá và xác nhận hoàn thành Spec Review thuộc về Product Owner hoặc Lead Architect cấp dự án.
   - Vai trò CODEX_PLAN hoàn toàn không quản lý hay xác nhận thẩm định Spec Review; vai trò này chỉ tiếp nhận bản đặc tả đã được dự án đồng thuận làm căn cứ đầu vào.
   - Chỉ khi tiền điều kiện Spec Review đã hoàn tất ở cấp dự án, quy trình kỹ thuật mới được phép kích hoạt bước lập kế hoạch thông qua lệnh `$plan-feature`.

### Stage 2: Implementation Planning and Plan Review (Global Workflow v2)
1. **Lập kế hoạch thực thi qua `$plan-feature`**: Khởi tạo kế hoạch chi tiết (Active Plan) xác định rõ:
   - `PLAN_ID`, `PLAN_VERSION`, `SCOPE`.
   - Repository Map và ranh giới Git root.
   - Danh sách file cụ thể sẽ thay đổi hoặc tạo mới.
   - Các lane triển khai tuần tự hoặc song song (phân định file ownership để tránh xung đột).
2. **Cổng kiểm duyệt kế hoạch (Plan Review Gate)**:
   - Kế hoạch phải được kiểm tra độc lập và cấp trạng thái `PLAN_REVIEW: APPROVED`.
   - Mọi thay đổi nội dung của plan sau khi duyệt sẽ làm tăng `PLAN_VERSION` và khiến review cũ mất hiệu lực (stale).

### Approval Metadata Reconciliation

`WORKFLOW_AMENDMENT_ID: APPROVAL-METADATA-RECONCILIATION`
`AMENDMENT_STATUS: HUMAN_AUTHORIZED`
`AUTHORIZATION_ROLE: PROJECT_OWNER_WORKFLOW_OWNER`
`AUTHORIZATION_DATE: 2026-10-19`

`PLAN_REVIEW` trong active Plan là snapshot metadata được ghi tại thời điểm
tạo hoặc cập nhật Plan; nó không phải một approval gate độc lập. Canonical
`.agent/reviews/plan-review.md` là nguồn xác thực trạng thái Plan Review khi
đồng thời thỏa mãn toàn bộ điều kiện sau:

1. Canonical review ghi `PLAN_REVIEW: APPROVED`.
2. `PLAN_ID` của review khớp active Plan.
3. `PLAN_VERSION` của review khớp active Plan.
4. SHA-256 byte UTF-8 của active Plan khớp tuyệt đối với `PLAN_SHA256` trong
   canonical review.
5. Không có approval mới hơn đã supersede hoặc revoke approval hiện tại.

Khi các điều kiện trên đạt, `CODEX_REVIEW` và `ANTI_DEV` dùng canonical review
để xác định Plan Review approval và implementation authorization. Không yêu
cầu, và không được thực hiện, việc sửa active Plan chỉ để đổi
`PLAN_REVIEW: PENDING` thành `PLAN_REVIEW: APPROVED`; việc sửa đó sẽ làm thay
đổi Plan hash và có thể làm approval stale.

Quy tắc này không áp dụng nếu Plan hash khác approved SHA, ID/version khác,
canonical review không `APPROVED`, approval đã bị supersede/revoke, hoặc có
thay đổi technical content sau approval. Các trường hợp đó phải đi qua Plan
Revision và Plan Review mới theo workflow.

### Stage 3: Implementation, Verification, and Handoff
1. **Thẩm quyền triển khai (Authorization Gate)**:
   - Agent hoặc kỹ sư chỉ được phép sửa mã nguồn khi có đúng một active plan kèm phê duyệt `PLAN_REVIEW: APPROVED` còn hiệu lực và mã băm SHA-256 khớp tuyệt đối.
2. **Quy tắc thực thi (Implementation Rules)**:
   - Chỉ chỉnh sửa trong phạm vi file đã đăng ký trong plan.
   - Tuân thủ coding guardrails và package visibility.
   - Chạy đầy đủ các bước kiểm thử tự động, build và verification được nêu trong plan.
3. **Cổng bàn giao thực thi (Implementation Handoff Gate)**:
   - Sau khi hoàn thành các lane và bước kiểm tra thực tế, Agent/kỹ sư triển khai bắt buộc phải xuất artifact bàn giao tổng hợp (Handoff Artifact) lưu tại `<PROJECT_ROOT>/.agent/handoffs/<PLAN_ID>.md` (hiển thị `IMPLEMENTATION_STATUS: READY_FOR_REVIEW`).
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
   - Kết quả đánh giá đạt chuẩn cấp trạng thái: `CODE_REVIEW: APPROVED`. Nếu có phát hiện lỗi: Đưa ra `CHANGES_REQUESTED`. Đội triển khai phải sửa chữa và bàn giao lại.

### Stage 4: Final Gate and Integration
- Xác nhận toàn bộ tiêu chí AC đã đạt và kiểm thử tự động vượt qua.
- Kết quả nghiệm thu cuối cùng cấp trạng thái: `FINAL_GATE: PASS`.
- Đóng gói tài liệu bàn giao và hoàn tất tính năng.

---

## Workflow State and Governance

- Thư mục `.agent/` lưu workflow state tại `PROJECT_ROOT`. Khi ghép nhánh authentication, giữ chính sách governance hiện tại trong `AGENTS.md`: không blanket-ignore `.agent/`; việc stage/commit các artifact và `AGENTS.md` do con người quyết định.
- Agent bắt buộc phải duy trì tính toàn vẹn của Global Workflow v2:
  - Duy trì tính duy nhất của active plan trong `.agent/plans/active/`.
  - Tuân thủ mã băm toàn vẹn SHA-256 (`PLAN_SHA256`) và ngăn chặn stale approval.
  - Tuyệt đối bảo toàn nguyên vẹn mọi workflow artifacts trong `archive/`, `cancelled/`, và `paused/` (không sửa đổi, không xóa, không tái cấu trúc).

---

## Contract and Specification Change Management

Khi tính năng đã bước vào giai đoạn triển khai hoặc đã hoàn thành mà phát sinh yêu cầu thay đổi:

1. **Thay đổi Hợp đồng API / Event hoặc Specification**:
   - Nếu thay đổi ảnh hưởng đến Consumer ở module khác hoặc Frontend: **Bắt buộc dừng việc triển khai ngay lập tức**.
   - Phải báo cáo leo thang (`ESCALATION: PLAN_REVISION_REQUIRED`).
   - Cập nhật lại Feature Spec và được dự án xác nhận lại tiền điều kiện. Sau đó lập phiên bản mới của Active Plan, tăng số hiệu phiên bản (`PLAN_VERSION`), và thực hiện kiểm duyệt lại để nhận `PLAN_REVIEW: APPROVED` mới.
2. **Thay đổi Logic Nội bộ trong Package `internal`**:
   - Các điều chỉnh hiện thực không làm thay đổi public contract, schema hay ranh giới module có thể được điều chỉnh trong cùng phiên bản kế hoạch nếu được Code Review chấp thuận.

---

## Git Safety and Automation Boundaries

Để bảo vệ lịch sử và sự ổn định của repository:

> [!CAUTION]
> 1. **Cấm tự động Git Mutation**: AI Agent tuyệt đối không tự ý thực hiện các lệnh: `git commit`, `git push`, `git reset`, `git clean`, `git rebase`, hoặc `git checkout --force`.
> 2. **Bảo toàn thay đổi chưa commit**: Khi bắt đầu bất kỳ phiên làm việc nào, Agent phải kiểm tra `git status` và bảo vệ toàn bộ các file chưa commit của lập trình viên.
> 3. **Quyền sở hữu Commit**: Mọi thao tác commit và push lên remote repository phải do kỹ sư con người trực tiếp rà soát và thực thi.
