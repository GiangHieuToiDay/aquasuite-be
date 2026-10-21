# Risk-L Feature Specification Template

> Chỉ bắt buộc cho work item Risk L theo `docs/workflow/feature-delivery.md`. Risk S/M ghi trực tiếp requirement trong Delivery Brief; không sao chép các mục không áp dụng.

## Metadata

- **Work ID**: `<WORK-ID>`
- **Feature / Use Case**: `<name>`
- **Version**: `<version>`
- **Owner**: `<person/team>`
- **Owning Module**: `<module>`
- **Risk Reasons**: `<security | money | inventory | concurrency | data migration | public contract | cross-module>`
- **Design Review**: `<PENDING | APPROVED>`
- **Decision Owner**: `<Product Owner / Tech Lead / Security Owner>`

## 1. Goal and Scope

### Problem and Desired Outcome

<Vấn đề cần giải quyết và kết quả quan sát được.>

### In Scope

- ...

### Out of Scope

- ...

## 2. Actors and Security

| Actor | Allowed Operations | Required Role/Permission | Data Restrictions |
|---|---|---|---|
| ... | ... | ... | ... |

- Sensitive data and logging rules: ...
- Authentication/authorization decisions: ...

## 3. Business Rules

1. ...

Chỉ thêm state transition table khi feature thực sự có lifecycle:

| From | Action | To | Conditions/Invariants |
|---|---|---|---|
| ... | ... | ... | ... |

## 4. Data and Migration Decisions

- Owned entities/tables: ...
- Cross-module references bằng scalar ID: ...
- Additive/destructive migration impact: ...
- Existing-data backfill/rollback/recovery: ...
- Index, uniqueness và database integrity: ...

Nếu không thay đổi schema hoặc dữ liệu, ghi `Not applicable` và bỏ các chi tiết còn lại.

## 5. Contracts

Chỉ mô tả loại contract thực sự bị thêm hoặc thay đổi.

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| ... | ... | ... | ... | ... |

### Inter-Module Java API

| Provider | Consumer | Method/DTO | Transaction | Errors/Compatibility |
|---|---|---|---|---|
| ... | ... | ... | ... | ... |

### Domain Event

| Event | Publisher/Consumers | Payload | Timing | Idempotency/Failure Handling |
|---|---|---|---|---|
| ... | ... | ... | ... | ... |

## 6. Concurrency, Transaction and Failure Safety

- Concurrent operations/invariants: ...
- Locking or uniqueness strategy: ...
- Transaction boundary: ...
- Partial failure and recovery: ...
- Retry/idempotency strategy when applicable: ...

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | ... | Unit/integration/security/manual test | ... |

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| ... | ... | Design Review / implementation / release | ... |

Không approve Design Review khi còn quyết định cản trở implementation an toàn. Các chi tiết implementation có thể hoàn tác dễ dàng không phải blocker của spec.
