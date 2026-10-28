# SCRUM08 - Customer account registration and email activation

Scope: Customer account registration, form validation, email OTP and reuse of the existing login JWT/session implementation. The account is stored as a UUID User in IAM with the fixed CUSTOMER role. Successful activation also creates the linked CRM profile in customers; this endpoint does not create staff or administrator accounts. Registration audit, Google/SMS, terms persistence and background cleanup are deferred.

## HTTP flow

1. `POST /api/v1/iam/register` with `fullName`, `email`, `phone`, `password`, `passwordConfirmation`, `termsAccepted: true`. Returns HTTP 201 with `data.id`, `data.email`, `data.status: PENDING_ACTIVATION`. The server assigns CUSTOMER and a generated username. Passwords are BCrypt hashes; pending users cannot log in.
2. `POST /api/v1/iam/register/verifications` with `email` and the six-character `otp`. Returns HTTP 200 with the existing LoginResponse fields: `access_token`, `refresh_token`, `token_type`, `expires_in`, `user`. Customer profile creation and account activation commit together, before session creation.
3. `POST /api/v1/iam/register/otp-resends` with `email` to request a replacement after 60 seconds. Replaces the old code without resetting unexpired failed attempts.

Only these three POST paths are public in addition to login/refresh. Other methods and paths retain authentication. Roles/status/userId from a request never determine account privileges. All responses use ApiResponse; validation has field errors. Credential-containing DTOs have redacted toString, and the controller handles form validation without logging rejected password/OTP values.

## Implementation

`RegisterController` validates DTOs and returns safe responses. `RegisterService` uses TransactionTemplate with UserRepository/RoleRepository. User creation uses atomic PostgreSQL INSERT ON CONFLICT DO NOTHING, so a duplicate race returns the domain conflict without generating a failed credential-bearing SQL log. User creation and CUSTOMER assignment commit together. Resend and activation lock the pending user row to serialize code rotation and verification; only accounts created by this registration flow (generated customer UUID username) are eligible.

OTP Redis keys are `iam:registration:otp:<user UUID>` with a 300-second TTL, BCrypt hash, version, send time and attempts. Five wrong attempts block the unexpired cycle. Lua operations store with TTL, increment attempts only for the current version, and atomically consume the current code. No OTP plaintext is stored in Redis or PostgreSQL. TTL automatically expires OTP data; there is no cleanup job or registration configuration subsystem.

Mail uses the existing JavaMailSender and existing SMTP environment configuration (`SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` and deployment SMTP/TLS settings). No new secret is required. A successful send means SMTP accepted the message, not that the inbox received it. Tests replace the sender and never send real mail.

`AuthService.loginRegisteredUser(UUID)` is internal IAM only, called after successful activation commit. `AuthServiceImpl` reloads the ACTIVE account/authorities and uses the same private createSession helper as password login. JwtTokenService/RedisSessionStore, signing, token types, TTL, refresh rotation and logout are unchanged. No registration JWT or token verifier is added.

CRM owns customers. IAM calls CustomerRegistrationApi through public DTOs in the same activation transaction (MANDATORY); user_id is a scalar UUID FK, with no JPA relationship to IAM internals. A profile contains customer_id, unique customer_code (CUS-UUID), customer_name, email, phone, RETAIL type, ACTIVE status and creation/update timestamps. Address/company are null because registration does not collect them. user_id is unique, and repeated identical creation returns the existing profile without overwriting it. The FK uses RESTRICT rather than silently deleting customer history when an account is removed.

## Failure recovery and limitations

- Duplicate email (case-insensitive) or phone, including an existing pending account: REG_001; no credential overwrite. Pending users may resend/verify.
- Incorrect OTP: REG_002, with attempts remaining. Missing/expired OTP: REG_003. Cooldown/exhausted attempts: REG_004.
- Already active/ineligible account: REG_006; verification cannot issue a second session. Use ordinary login.
- Email failure: REG_007, account remains pending. Request another OTP after cooldown.
- DB/Redis/role/CRM failure: REG_008; activation and profile creation roll back together. If OTP consumption succeeded before DB rollback, resend a new OTP.
- Activation committed but session creation failed: REG_009, account remains ACTIVE. Use existing `POST /api/v1/auth/login`; never remove the committed account.

Pending users remain in PostgreSQL because background cleanup was explicitly excluded. Per-IP/contact rate limits and broad abuse controls are outside this simplified ticket. FullName is persisted in users; termsAccepted is validated without a separate consent/version table. Provision the active CUSTOMER role through the existing owner-approved process before enabling real registration; the endpoint does not create roles or guess privileges.

Migration V6 adds nullable full_name and a unique non-null phone index. V7 creates customers with a unique UUID user_id FK to users. Existing users are preserved without guessing/backfilling customer profiles. Reconcile legacy duplicate/noncanonical phones manually if V6 preflight fails; do not delete accounts or rewrite V1-V6. Migration verification runs only on disposable PostgreSQL databases.
