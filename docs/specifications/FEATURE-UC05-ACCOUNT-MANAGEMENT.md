# Feature Specification — UC-05 Manage User Accounts and Status

## Metadata and Governance

- **Feature ID**: `FEATURE-UC05-ACCOUNT-MANAGEMENT`
- **Feature Name**: Manage User Accounts and Status
- **Version**: `0.3.0-draft`
- **Owner / Author**: AquaSuite Backend Team
- **Owning Modules**: `iam`, `audit`
- **Repository Scope**: `BE (aquasuite-be)`
- **Target Packages**: `com.aquasuite.ims.module.iam`, `com.aquasuite.ims.module.audit`
- **Database Baseline**: PostgreSQL; additive Flyway V3 after the tracked V1 and V2 migrations
- **Spec Review Status**: `APPROVED`
- **Spec Review Approver**: `Product Owner / Lead Architect`
- **Risk Level**: `HIGH`

## 1. Scope and Business Outcome

An authenticated Store Owner can list, filter, inspect, create, edit, and
activate, deactivate, or lock system accounts. A newly created account is
immediately `ACTIVE`, is assigned one or more roles selected through the
multi-select form by this use case,
has `provider=LOCAL` and `isVerified=false`, and receives a temporary-password
email after the database transaction commits. Email delivery failure must not
roll back account creation.

The dashboard contains total, active, inactive, and locked account counts;
keyword, role, and status filters; pagination; and account activity history.

### Out of Scope

- Email-verification and social-login flows.
- Persisting password-recovery OTPs or rate-limit state.
- Role/permission catalog administration, deletion, soft deletion, FCM-token
  management, and an SMTP/outbox worker implementation beyond a logging
  notification adapter.
- Changing V1 or V2 migrations.

## 2. Authorization and Security

| Actor | Required authority | Operations |
|---|---|---|
| Store Owner | `ROLE_SHOP_OWNER` | All UC-05 account actions |
| Other authenticated user | none | No UC-05 access |
| Audit listener | internal | Persist event-derived audit log |

- The caller identity comes from the UUID JWT subject through `IamApi`.
- The caller cannot deactivate or lock their own account.
- An operation cannot leave zero active `ROLE_SHOP_OWNER` accounts.
- Every status transition that leaves `ACTIVE` must revoke every tracked Redis
  session for the target user after commit. Database status validation remains
  the immediate authorization guard for every subsequent access-token request.
- Password hashes, raw temporary passwords, tokens, OTPs, verification codes,
  and provider credentials never appear in HTTP responses, logs, audit JSON,
  or public events.

## 3. Account Rules

| Action | Result |
|---|---|
| Create | Validate unique username/email/phone; persist `ACTIVE`, `LOCAL`, `false`; set the selected `user_roles` rows; publish a non-sensitive event; send email after commit. |
| Edit | Change username, email, phone, avatar URL, and the selected roles; status is immutable through this endpoint. |
| Deactivate | `ACTIVE -> INACTIVE`; revoke target sessions after commit. |
| Lock | `ACTIVE -> LOCKED`; revoke target sessions after commit. |
| Reactivate | `INACTIVE|LOCKED -> ACTIVE`; does not issue credentials or restore old sessions. |

Phone is a database-unique value. The UI accepts the locally normalized `0...`
form only; `+84...` input is rejected. The service performs a friendly
duplicate check, while PostgreSQL's unique index is the final concurrency
guard. A duplicate-key `DataIntegrityViolationException` is mapped to MSG25
only when it identifies the phone, username, or email unique constraint.

## 4. Data Model and Migration

The attached logical DB-v2 note is treated as desired schema, but the tracked
repository migrations remain the executable baseline. `V3` is additive only:

- add `users.provider` (`VARCHAR(50)`, default `LOCAL`), `provider_id`
  (`VARCHAR`, nullable), and `is_verified` (`BOOLEAN`, default `false`);
- add the `uq_users_phone` unique index on non-null `users.phone` after an
  explicit preflight check that stops migration when duplicate existing phone
  values are found;
- add `user_roles.assigned_at` (non-null/default current timestamp) and
  `user_roles.assigned_by` (nullable UUID scalar); and
- create `audit_logs` with `audit_id`, scalar actor `user_id`, entity type/id,
  action, old/new JSONB snapshots, reason, and creation timestamp.

`password_hash` remains the only password column. The legacy verification-code,
email-counter, and password-recovery fields are not added for UC-05 because
the approved recovery flow keeps its OTP state in memory.

No cross-module JPA relationship is introduced. `audit_logs.user_id` and
`assigned_by` remain scalar UUIDs.

## 5. Module Contracts

### HTTP API

All responses use the existing `ApiResponse<T>` convention.

| Method and route | Purpose |
|---|---|
| `GET /api/v1/iam/users/stats` | Account dashboard counts |
| `GET /api/v1/iam/users` | Paginated keyword/role/status search |
| `GET /api/v1/iam/users/{id}` | Account detail |
| `GET /api/v1/iam/roles?assignable=true` | Active roles for the multi-select form |
| `POST /api/v1/iam/users` | Create account |
| `PATCH /api/v1/iam/users/{id}` | Edit identity/profile and all selected roles |
| `PATCH /api/v1/iam/users/{id}/status` | Explicit status transition |
| `GET /api/v1/audit-logs` | Paginated activity history filterable by user/entity |

Duplicate username, email, or phone returns the approved MSG25 business error.
Unauthorized access returns the centralized authorization error envelope.

### Public domain events

IAM publishes immutable records under `iam/api/event`:

- `UserAccountCreatedEvent`
- `UserAccountUpdatedEvent`
- `UserAccountStatusChangedEvent`

Each has `eventId`, actor UUID, target-user UUID, occurred time, operation
metadata, and non-sensitive old/new values. The Audit module listens with
`@TransactionalEventListener(phase = AFTER_COMMIT)`, deduplicates by
`eventId`, and writes `audit_logs`. Audit never calls IAM internals.

## 6. Legacy Reconciliation

The old Long-ID IAM implementation is source-preserved but is structurally
relocated as a complete tree under `iam/internal/legacy/**`. Its tests move
under the matching legacy test package. Canonical recovery DTOs/enums that are
still imported by active code move into `iam/internal/auth/**` first, so no
canonical runtime class depends on legacy packages. The canonical UUID User,
repository, status enum, auth, and authorization remain the only scanned IAM
runtime implementation.

## 7. Verification and Acceptance Criteria

- [ ] Only one User entity/repository/status model is outside `internal/legacy`.
- [ ] Store Owner can manage account list/detail/create/edit/status only through
  the documented endpoints and receives the existing API envelope.
- [ ] New account is active, local, unverified, has every selected role, and
  does not leak a temporary password.
- [ ] Phone duplicate prevention works across concurrent application instances
  through the `uq_users_phone` database unique index.
- [ ] Self-lock/deactivate and last-active-Store-Owner transitions are rejected.
- [ ] Inactive/locked target sessions are revoked and access is denied.
- [ ] Audit rows are generated after committed IAM changes without sensitive
  data.
- [ ] V3 migrates an empty V1/V2 database and validation succeeds.
