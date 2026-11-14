# Shop Owner access to accountant operations

Decision: repository owner confirmed on 2026-11-12 that `SHOP_OWNER` may perform
the existing operations allowed to `ACCOUNTANT`.

This expands the earlier accountant-only authorization descriptions for:

- `/api/v1/finance/payment-verifications`: list, detail, verify and reject.
- Fulfillment delivery management: list, detail, assignment, scheduling and photos.
- Finance COD: accept full receipt of declared COD cash or reject the declaration (transaction-only UC30).

Both HTTP controller and service checks accept `SHOP_OWNER`. Existing endpoints
already accepting both roles retain their behavior. Request/response payloads,
database schema and account role assignments are unchanged.

The authenticated owner's ID is recorded as the actor. COD self-review and
self-remittance restrictions still apply. Shipper-only collection declarations
and delivery start/complete/fail operations still require `SHIPPER` and their
existing ownership checks.

Restart or redeploy the backend with the updated code to apply this policy.
