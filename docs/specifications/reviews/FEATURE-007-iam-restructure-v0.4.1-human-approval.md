# FEATURE-007 IAM Restructure Specification Approval Record

ARTIFACT_TYPE: HUMAN_SPEC_APPROVAL_RECORD
SPEC_ID: FEATURE-007-IAM-RESTRUCTURE-SCOPE-CHANGE
SPEC_VERSION: 0.4.1-proposed
SPEC_SHA256: 873AC31EE787F315C8019AFDB48DB354E149DBA4ECBFFECB0F909F5895F6A28B
SPEC_DECISION: APPROVED
APPROVER: Project Decision Owner
ROLE: Project Owner / Authorized Decision Maker
APPROVAL_DATE: 2026-10-18
IMPLEMENTATION_AUTHORIZED: NO
PLAN_REVISION_STATUS: PENDING_CODEX_REVIEW

## Approved Scope

Người phê duyệt xác nhận phạm vi sau:

1. Tái cấu trúc IAM theo kiến trúc đã chốt.
2. Supersede năm protected FEATURE-006 paths.
3. Giữ nguyên `UserExternalService.java`.
4. Chấp nhận public Java DTO package change.
5. Giữ nguyên HTTP, database, JWT, Redis và Authentication behavior.
6. Thực hiện IAM restructuring trước khi tiếp tục Authentication implementation.

## Authorization Boundary

Approval này cho phép ghi nhận governance record và yêu cầu CODEX_REVIEW kiểm tra revision preconditions.

Approval này không cho phép:

- Sửa production source hoặc test source.
- Sửa FEATURE-007 Plan v2.
- Tạo FEATURE-008 active plan.
- Tự phát hành review verdict.
- Reset, clean, commit hoặc push.

## Parent Plan Integrity

Active plan tiếp tục được bảo toàn:

```text
PLAN_ID: FEATURE-007
PLAN_VERSION: 2
PLAN_SHA256: A4BE0576E0D782DB99B11B83258D653FD56AC9552D79EC61793FB23B7F6F0B33
PLAN_REVIEW: APPROVED
```

## Dirty Working Tree Baseline

Baseline được ghi nhận tại thời điểm approval record được tạo:

```text
 M .env.example
 M pom.xml
 M src/main/java/com/aquasuite/ims/module/iam/internal/repository/UserRepository.java
 M src/main/resources/application-prod.yml
 M src/main/resources/application-test.yml
 M src/main/resources/application.yml
?? docs/specifications/FEATURE-007-authentication-foundation.md
?? docs/specifications/FEATURE-007-iam-restructure-change.md
?? docs/specifications/reviews/FEATURE-007-v0.3.1-approval.md
?? src/main/java/com/aquasuite/ims/module/iam/internal/controller/AuthController.java
?? src/main/java/com/aquasuite/ims/module/iam/internal/dto/
?? src/main/java/com/aquasuite/ims/module/iam/internal/error/
?? src/main/java/com/aquasuite/ims/module/iam/internal/security/
?? src/main/java/com/aquasuite/ims/module/iam/internal/service/AuthService.java
?? src/main/java/com/aquasuite/ims/module/iam/internal/service/AuthServiceImpl.java
?? src/main/java/com/aquasuite/ims/module/iam/internal/session/
?? src/main/resources/redis/
?? src/test/java/com/aquasuite/ims/module/iam/internal/auth/
?? src/test/java/com/aquasuite/ims/testsupport/AuthIntegrationTestSupport.java
```

Các thay đổi trên là dirty baseline của user và phải được bảo toàn nguyên trạng.

## Review Request

CODEX_REVIEW được yêu cầu xác minh độc lập:

- Approval record có đúng `SPEC_ID`, version và SHA-256 hay không.
- Scope change có conflict với FEATURE-007 Plan v2 hay không.
- Protected-file supersede scope có đủ điều kiện để revise plan hay không.
- Có thể tạo `FEATURE-007` Plan v3 với version tăng đúng một lần hay chưa.
- Dirty baseline có được bảo toàn hay không.

CODEX_PLAN không tự tạo `PLAN_REVIEW: CHANGES_REQUESTED` và không tuyên bố Plan Revision được phép trước khi CODEX_REVIEW phát hành finding thực tế.
