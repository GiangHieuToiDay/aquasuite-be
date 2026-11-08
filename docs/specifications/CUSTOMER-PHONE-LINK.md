# Counter customer and account linking by phone

Work ID: CUSTOMER-PHONE-LINK. Risk L: cross-module identity, historical data, concurrency.
Design review: APPROVED by user decisions on 2026-11-08 (BE only; automatic phone match).

## Goal and scope
At checkout create/reuse a CRM customer without an IAM account. Orders reference the stable customer_id.
At email-OTP registration activation, attach the new user to the existing ACTIVE customer with the same trimmed Vietnamese mobile phone. If absent, create a profile and link it in the same transaction.
Existing CRM names, emails, addresses, credit profile, customer_id/code, orders and payments remain unchanged.
No retrospective reassignment of anonymous walk-in orders: their original phone is not reliably recorded.

## Actors and security
Existing sales order role checks remain. Registration keeps current email OTP, terms/password and CUSTOMER-role rules.
Owner explicitly chose automatic phone matching without phone OTP or staff approval. Email OTP proves only email ownership: another person who enters an unclaimed customer's phone can obtain that profile's history. This accepted policy is recorded here, not represented as verified phone ownership.
Never relink a profile that already has another user, nor reactivate an inactive/blocked profile.

## Contracts
Existing POST /api/v1/sales/orders: customerId takes precedence; otherwise valid customerPhone reuses/creates CRM RETAIL profile, name optional (max 150). Missing/blank phone retains anonymous walk-in behavior. Phone is trimmed and must match 0[35789][0-9]{8}.
CrmApi.getOrCreateCounterCustomer(phone, name) is additive; joins caller transaction. Sales invokes it without accessing CRM internals. No new HTTP route.
CustomerRegistrationApi.createProfile retains signature but now links by phone or creates a new profile. Linked/inactive conflict raises DB_001 (409) through registration and rolls back user creation. Same user+phone retry is idempotent.
Registration/login HTTP payloads remain unchanged. Login stays email/password.

## Data and migration
V27 adds unique customer phone index with non-mutating preflight. Duplicate or whitespace-padded existing phone values fail migration and require explicit reconciliation; no records are merged/deleted. Other legacy phone formats are preserved but cannot be newly submitted through this flow. Anonymous 0000000000 is never a registration match.
Customer.user_id stays nullable; no cross-module JPA association. No migration of order foreign keys.

## Transaction and concurrency
Native INSERT ON CONFLICT DO NOTHING avoids duplicate profile creation across POS/registration races. Registration locks the phone row before assigning user_id. Existing unique user_id and IAM phone/email indexes remain authoritative.
Profile creation/link and user activation share the registration transaction. Order and new counter profile share the existing sales transaction, so failures do not leave standalone profiles. Failed activation rolls back and releases the OTP claim using current recovery.

## Acceptance and verification
- First phone purchase creates one unlinked customer and order references it; repeated phone purchases reuse it.
- Activation links original customer_id and preserves CRM fields/order history.
- Brand-new registration creates and links one profile.
- Already-linked/blocked conflicts do not reassign any customer; user activation rolls back.
- Concurrent same-phone creation yields one customer. Migration refuses ambiguous data.
- Existing FE payload is sufficient; no frontend changes for this work item.
Targeted unit tests plus PostgreSQL integration tests for uniqueness, concurrency, history preservation and rollback. No FE changes or checks required after scope correction. No live SMTP required by unchanged OTP flow.

Anonymous walk-in creation also uses conflict-safe insertion so the new unique index does not break simultaneous anonymous checkouts.

## Verification result
59 targeted tests passed with zero failures/errors/skips, including real PostgreSQL concurrency/migration and email OTP activation linking an existing customer with order history. Full-suite and live SMTP checks were not run. No FE changes.
