# WORK-016 — Customer phone/password login

## Metadata
- Version: 0.1
- Owning Module: iam
- Risk: L — authentication and public HTTP contract
- Design Review: APPROVED — user confirmed 2026-11-03; phone/password for CUSTOMER, email flow unchanged.
- Decision Owner: Product Owner / Security Owner

## 1. Goal and Scope
Add customer phone/password login after SMS registration. Preserve existing email login behavior and reuse password verification, JWT issuance and Redis sessions. No changes to registration, password recovery or CRM.

## 2. Actors and Security
Guest may submit credentials. Phone login requires an ACTIVE account with an active CUSTOMER role. Client cannot select roles or account status. Never log passwords, tokens or submitted phone numbers. Incorrect password, unknown phone and non-customer accounts return the same invalid-credentials response. Check password before exposing inactive account status.

## 3. Business Rules
1. Keep POST /api/v1/auth/login with email/password unchanged. Current implementation does not restrict this route to employees; eligible customers can also use email.
2. Add POST /api/v1/auth/customer-login with phone/password.
3. Trim phone and accept the same Vietnamese mobile format as registration: 0[35789][0-9]{8}. Do not transform password.
4. Resolve stored phone exactly, verify BCrypt password through the existing authentication infrastructure, require active CUSTOMER role and ACTIVE user.
5. Reuse existing session creation and LoginResponse; no OTP or Twilio request on login. Refresh/logout remain unchanged.

## 4. Data and Migration Decisions
Read existing IAM users.phone, password_hash, status and roles. No schema, migration or backfill. Add a roles/permissions-fetching phone query in IAM repository.

## 5. Contracts
| Method/path | Authorization | Request | Success | Errors |
|---|---|---|---|---|
| POST /api/v1/auth/customer-login | Public | phone: required Vietnamese mobile; password: required, max 100 characters | 200 ApiResponse<LoginResponse>, same token/session fields as email login | Existing validation error; existing invalid-credentials error; AUTH_008 after valid credentials for inactive account; AUTH_009 for unavailable dependencies |

No inter-module API or domain event changes.

## 6. Concurrency, Transaction and Failure Safety
Reuse existing JWT/session TTL, Redis session creation and current-authorities resolution. Do not issue a response containing usable tokens unless session creation succeeds. Database/Redis errors fail closed; no account creation or mutation during login. Preserve existing password authentication timing protection for unknown users. Existing deployment rate limiting requirements also apply to the new public route.

## 7. Acceptance Criteria and Verification
| AC | Expected outcome | Verification |
|---|---|---|
| 01 | Verified ACTIVE CUSTOMER logs in by registered phone/password | Service and HTTP tests |
| 02 | Unknown phone, wrong password or employee-only account creates no session | Negative security tests |
| 03 | Pending/inactive/locked accounts cannot log in; inactive CUSTOMER role denied | Status/role tests |
| 04 | Missing/invalid phone or password rejected; password whitespace preserved | Validation tests |
| 05 | Email login, registration auto-login, refresh and logout retain behavior | Existing IAM regression tests |
| 06 | DB/Redis failure produces no successful login | Failure tests |

## 8. Open Decisions
Approve additive customer-login endpoint and CUSTOMER-only phone login. Do not silently change existing email login to employee-only; that would be a separate authorization change.
