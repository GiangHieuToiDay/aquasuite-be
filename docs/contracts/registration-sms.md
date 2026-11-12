# Registration email OTP

Updated by owner request on 2026-11-08; replaces the SMS registration flow.
The existing filename is retained for incoming links.

POST /api/v1/iam/register accepts fullName, email, phone, password,
passwordConfirmation and termsAccepted. Phone remains required for the CRM profile,
but OTP is sent only to the submitted email through SMTP.
The response contains id, email and status PENDING_ACTIVATION; no database account exists yet.
POST /api/v1/iam/register/verifications accepts email and a six-digit otp.
Success creates the ACTIVE CUSTOMER account and CRM profile atomically and returns
the existing login response. POST /api/v1/iam/register/otp-resends accepts email.

Configure MAIL_HOST, MAIL_PORT, MAIL_USERNAME and MAIL_PASSWORD in the backend
environment. Never store real credentials in source or documentation.
SMTP failure returns 503 REG_007 and leaves the pending form available for resend.
Registration does not call Twilio.

Pending forms expire five minutes after initial registration. Resend does not
extend expiry. Cooldown is 60 seconds, with at most three sends per normalized
email in ten minutes and five verification attempts per form.
Each resend generates a new secure six-digit code and invalidates the previous one.
Only BCrypt hashes are stored. Redis claims serialize send and verify operations.
Database uniqueness protects email and phone; one pending form per email or phone.
Account/CRM rollback releases the claim so the approved code can be retried.

Redis namespace v4 isolates this flow from old SMS challenges. In-flight SMS
registrations must restart after deployment. No database migration or account
backfill is required. Existing customers log in using their stored email.


## Counter customer linking (2026-11-08)
After email OTP, registration links the new user to the existing ACTIVE unlinked CRM customer by phone; otherwise creates one. Existing customer IDs/details/order history are preserved. Linked or inactive conflicts return DB_001 and roll back activation. Owner chose automatic phone matching without phone OTP. See ../specifications/CUSTOMER-PHONE-LINK.md and migration V27 preflight.


## SMTP bounds and verification (2026-11-08)
The custom JavaMailSender now applies connection/read/write socket timeout defaults of
5000/3000/5000 ms, configurable through standard spring.mail.properties.mail.smtp
connectiontimeout, timeout and writetimeout properties. Registration remains synchronous;
mail errors return REG_007 and never report delivery success before the send completes.
No real SMTP delivery is implied by mocked transport tests.

The Redis registration-expiry regression compares the absolute PEXPIRETIME before and
after resend. This still detects any actual extension without relying on relative TTL
remaining monotonic across wall-clock adjustments. Runtime expiry and resend limits are unchanged.
