# AquaSuite Lean Feature Delivery Workflow

## Purpose

Quy trình này tối ưu vòng đời phát triển với AI theo nguyên tắc: kiểm soát theo rủi ro, một nguồn trạng thái duy nhất và ưu tiên bằng chứng chạy được hơn tài liệu lặp lại.

Các ràng buộc kiến trúc, bảo mật, module boundary và Git safety trong `AGENTS.md` luôn có hiệu lực, không phụ thuộc mức rủi ro của công việc.

```text
Intake & Risk Classification
          |
          v
Delivery Brief / Feature Spec (khi cần)
          |
          v
Implement in Small Vertical Slices <-> Targeted Tests
          |
          v
One Review Gate
          |
          v
Done
```

---

## 1. Classify the Work

Mọi công việc phải được phân loại trước khi lập kế hoạch. Mức rủi ro cao nhất áp dụng nếu công việc khớp nhiều nhóm.

| Level | Typical Work | Required Artifact | Approval Before Coding |
|---|---|---|---|
| `S` — Small | Bug nhỏ, refactor nội bộ, bổ sung test, tài liệu hoặc config không đổi contract | Delivery Brief ngắn | Không; trừ khi còn quyết định nghiệp vụ |
| `M` — Standard | Endpoint/CRUD trong một module, migration additive đơn giản, thay đổi nghiệp vụ giới hạn | Delivery Brief đầy đủ | Một lần xác nhận scope/AC bởi owner |
| `L` — High Risk | Authentication/authorization, tiền, tồn kho, concurrency, migration có rủi ro dữ liệu, public API/event hoặc thay đổi xuyên module | Feature Spec + Delivery Brief | Một Design Review bởi đúng owner |

Không dùng số file hoặc số dòng làm tiêu chí chính. Phân loại dựa trên tác động, khả năng hoàn tác và mức độ không chắc chắn.

### Mandatory Intake Questions

1. Mục tiêu và giá trị nghiệp vụ là gì?
2. Acceptance Criteria nào chứng minh công việc hoàn thành?
3. Module nào sở hữu dữ liệu và logic?
4. Có thay đổi HTTP API, Java API hoặc Domain Event công khai không?
5. Có thay đổi schema, migration hoặc dữ liệu hiện hữu không?
6. Có rủi ro security, tiền, tồn kho, concurrency hoặc mất dữ liệu không?

Nếu câu trả lời chưa đủ để triển khai an toàn, ghi quyết định mở trong Delivery Brief và chỉ dừng phần công việc phụ thuộc quyết định đó.

---

## 2. Single Source of Workflow State

Mỗi work item đang hoạt động sử dụng đúng một Delivery Brief:

```text
.agent/work/<WORK_ID>.md
```

Delivery Brief là nguồn trạng thái duy nhất. Không tạo riêng plan review, implementation handoff, code review hoặc final gate artifact. Không dùng SHA-256 của plan làm điều kiện cấp quyền triển khai.

### Minimum Delivery Brief

```markdown
# <WORK_ID> — <Title>

STATUS: DRAFT | DESIGN_REVIEW | READY | IN_PROGRESS | REVIEW | CHANGES_REQUESTED | BLOCKED | DONE | CANCELLED
RISK: S | M | L
OWNER_MODULE: <module>
SPEC: <path | NOT_REQUIRED>
DESIGN_REVIEW: NOT_REQUIRED | PENDING | APPROVED | CHANGES_REQUESTED

## Goal
<Outcome cần đạt>

## Acceptance Criteria
- [ ] AC-01 ...

## Scope
### In
- ...
### Out
- ...

## Decisions and Constraints
- ...

## Contract / Schema Impact
- None | ...

## Implementation Notes
- Các component/vertical slice dự kiến; không cần khóa danh sách file.

## Verification
- [ ] Targeted tests: <command/result>
- [ ] Full suite: <command/result hoặc lý do N/A>
- [ ] Contract/security/manual checks: <result>

## Review Findings
- [ ] <finding hoặc None>

## Completion
- Files/components changed: ...
- Known limitations: ...
```

Artifact cũ trong `.agent/plans/archive`, `.agent/plans/cancelled` và các thư mục lịch sử được bảo toàn nguyên trạng. Không cần chuyển đổi lịch sử sang format mới.

### Status and Design Review Rules

- `DRAFT`: Requirement hoặc quyết định bắt buộc chưa đủ để chuyển giao.
- `DESIGN_REVIEW`: Risk L đã đủ thông tin để CODEX_REVIEW đánh giá thiết kế.
- `READY`: Work item đủ điều kiện để ANTI_DEV triển khai.
- `IN_PROGRESS`: ANTI_DEV đang triển khai và kiểm thử.
- `REVIEW`: Implementation và verification đã sẵn sàng cho CODEX_REVIEW.
- `CHANGES_REQUESTED`: Còn review findings cần xử lý.
- `BLOCKED`: Cần quyết định hoặc điều kiện bên ngoài để tiếp tục.
- `DONE`: AC và verification đạt, không còn blocking finding.
- `CANCELLED`: Chỉ dùng khi người dùng hủy work item rõ ràng.

`DESIGN_REVIEW` được dùng như sau:

- Risk S/M: `DESIGN_REVIEW: NOT_REQUIRED`.
- Risk L chưa review: `DESIGN_REVIEW: PENDING` cùng `STATUS: DESIGN_REVIEW`.
- Risk L đạt review: `DESIGN_REVIEW: APPROVED` cùng `STATUS: READY`.
- Risk L chưa đạt: `DESIGN_REVIEW: CHANGES_REQUESTED` cùng `STATUS: CHANGES_REQUESTED`.

---

## 3. Specification and Planning Depth

### Risk S

- Goal, AC, scope và verification trong Delivery Brief.
- Có thể triển khai ngay nếu không có quyết định nghiệp vụ hoặc contract còn mở.

### Risk M

- Delivery Brief đầy đủ.
- Owner xác nhận scope và AC một lần trước khi triển khai.
- Ghi contract/schema impact nếu có.

### Risk L

- Feature Spec theo `docs/templates/feature-spec.md`.
- Design Review tập trung vào quyết định có chi phí hoàn tác cao: security model, public contract, schema/data migration, transaction và concurrency.
- Sau khi Design Review được duyệt, implementation không cần một Plan Review thứ hai.

Kế hoạch mô tả component hoặc vertical slice, không khóa theo danh sách file. Agent được phép thêm hoặc sửa file phát hiện trong lúc triển khai nếu vẫn nằm trong owning module, scope, contract/schema decision và AC đã duyệt.

---

## 4. Implementation Loop

Triển khai theo vertical slice nhỏ:

```text
Inspect current behavior
→ Implement one coherent slice
→ Run targeted tests
→ Inspect diff and boundaries
→ Repeat
```

Với backend, một slice có thể đi từ migration/entity đến repository/service, controller/security và integration test. Không chờ hoàn thành toàn bộ feature mới kiểm thử.

Trong quá trình thực thi:

- Bảo toàn mọi thay đổi chưa commit có sẵn của người dùng.
- Tuân thủ `api/` và `internal/` boundary.
- Không mở rộng scope chỉ để “tiện thể” refactor.
- Ghi quyết định mới có ý nghĩa vào Delivery Brief, không tạo artifact phụ.
- Cập nhật `STATUS: REVIEW` khi implementation và verification đã sẵn sàng để đánh giá.

---

## 5. One Review Gate

Một review duy nhất đánh giá:

1. Acceptance Criteria và behavior thực tế.
2. Module boundary và kiến trúc.
3. Public contract và schema compatibility.
4. Security, dữ liệu nhạy cảm và secret exposure.
5. Test evidence thực tế.
6. Diff ngoài scope và known limitations.

Kết quả review được ghi trực tiếp vào Delivery Brief:

- `DONE`: AC đạt, verification phù hợp rủi ro và không còn finding bắt buộc.
- `CHANGES_REQUESTED`: có finding cụ thể cần sửa.
- `BLOCKED`: cần quyết định hoặc điều kiện bên ngoài mà nhóm không thể tự giải quyết.

Khi có `CHANGES_REQUESTED`, bổ sung checklist finding vào cùng Delivery Brief, sửa code, chạy regression test và review lại. Không tạo version plan, handoff, RCA hoặc review artifact mới cho mỗi vòng.

RCA riêng chỉ cần cho incident production, security incident, mất/corrupt dữ liệu hoặc defect nghiêm trọng tái diễn.

---

## 6. Definition of Done

Work item chỉ chuyển thành `DONE` khi:

- Tất cả AC đã đạt.
- Targeted tests pass.
- Full test suite pass; hoặc ghi rõ lý do hợp lệ cho `NOT_APPLICABLE`/`NOT_RUN`.
- Contract, schema và security checks áp dụng đã hoàn thành.
- Không còn review finding bắt buộc hoặc quyết định mở cản trở release.
- Delivery Brief ghi các component/file đã thay đổi và known limitations.
- Diff đã được kiểm tra để tránh rò rỉ credential.

Review đạt cùng test evidence chính là final gate; không tạo Final Gate artifact riêng.

---

## 7. Change and Escalation Rules

Phải dừng phần implementation bị ảnh hưởng và xin quyết định khi phát sinh:

- Breaking change đối với HTTP API, Java API hoặc Domain Event công khai.
- Migration có nguy cơ mất, ghi đè hoặc diễn giải lại dữ liệu.
- Thay đổi ownership hoặc dependency giữa module.
- Thay đổi authentication, authorization hoặc permission semantics chưa được duyệt.
- AC/spec mâu thuẫn với running code hoặc với tài liệu kiến trúc.
- Scope mới làm thay đổi đáng kể risk classification.

Không cần re-plan hoặc re-approval cho:

- Thêm test, fixture hoặc test helper.
- Đổi tên/tách private implementation trong `internal/`.
- Sửa mapper, validation hoặc error handling để đáp ứng AC.
- Thêm file trong cùng module và scope.
- Điều chỉnh implementation không làm đổi public contract hoặc schema decision.

Nếu escalation làm thay đổi mục tiêu, AC, public contract hoặc schema decision, cập nhật cùng Delivery Brief; với Risk L, cập nhật Feature Spec liên quan và review lại đúng phần thay đổi.

---

## 8. Git and Artifact Safety

- Agent không tự ý chạy `git commit`, `git push`, `git reset`, `git clean`, `git rebase` hoặc `git checkout --force`.
- Commit và push thuộc quyền quyết định của con người.
- `.agent/` là local-only và được ignore bởi Git.
- Không sửa, xóa hoặc tái cấu trúc artifact đã archive/cancelled từ workflow cũ.
- Work item mới chỉ dùng `.agent/work/`; khi hoàn thành có thể chuyển nguyên file sang `.agent/archive/` mà không nhân bản.
- Không ghi secret thật vào source, tài liệu hoặc workflow artifact.

---

## 9. Workflow Health Metrics

Sau mỗi 2–3 work item, theo dõi:

- Thời gian từ intake đến lần chạy code/test đầu tiên.
- Số lần cần con người phê duyệt.
- Số artifact trên mỗi work item.
- Số vòng `CHANGES_REQUESTED`.
- Defect lọt qua review/test.

Mục tiêu mặc định: một workflow artifact cho mỗi work item, tối đa một approval trước coding và không tạo tài liệu không hỗ trợ một quyết định hoặc bằng chứng kiểm chứng cụ thể.
