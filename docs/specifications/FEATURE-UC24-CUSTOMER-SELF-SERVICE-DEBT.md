# FEATURE-UC24-CUSTOMER-SELF-SERVICE-DEBT — Approved Design

## Metadata

- **Work ID**: `UC24-CUSTOMER-DEBT-RECEIVABLES` (approved scope change)
- **Feature / Use Case**: UC-24 customer self-service debt view
- **Version**: `0.1.0`
- **Owner**: Project Owner / Technical Lead
- **Owning Module**: `finance`
- **Risk Reasons**: authorization, money, public HTTP contract, cross-module integration
- **Design Review**: `APPROVED`
- **Decision Owner**: Project Owner / Technical Lead
- **Status**: Approved and implemented

## 1. Goal and Scope

### Problem and Desired Outcome

Allow an authenticated customer to view only their own current receivables and the order/invoice information that explains the balance. This changes the earlier UC-24 scope, which excluded a customer self-service debt portal.

### In Scope

- One read-only endpoint for the authenticated `CUSTOMER`, with the existing route retained as a compatible alias.
- Resolve the CRM customer profile from the authenticated IAM user ID; never accept a customer ID from the caller.
- Return a minimized balance summary and that customer's order/invoice debt details.

### Out of Scope

- Payment recording, payment allocation, invoice downloads, or payment-proof access.
- Internal reminders, call notes, audit history, customer contact details, credit limits, and internal credit-risk classifications.
- Creating or linking a CRM customer profile as a side effect of reading debt.
- Any database migration or changes to debt calculations.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role | Data Restrictions |
|---|---|---|---|
| Customer | Read own debt summary and related order/invoice balances | `CUSTOMER` | Identity is derived from the authenticated principal; no caller-selected customer ID |
| Staff roles | No change proposed to existing UC-24 access | Existing role rules | Existing finance-management endpoints remain staff-only |

- Resolve the caller with `IamQueryService.getCurrentUserId()` and the existing CRM `CustomerService.getCustomerByUserId(userId)` contract.
- If no linked CRM profile exists, return the established not-found response; do not auto-create a profile.
- Do not return or log another customer's identifiers or financial data.
- The endpoint is informational and does not authorize or mutate transactions.

## 3. Business Rules

1. Reuse UC-24's existing debt calculation as the source of truth; do not calculate a second balance in the controller.
2. Do not add invoice receivables to order receivables when calculating the customer-level outstanding balance.
3. Include only the authenticated customer's debt documents in the response.
4. Do not expose internal reminder/audit records, receipts or payment evidence, credit limits, or internal debt classifications.
5. The query is read-only and has no side effects.

## 4. Data and Migration Decisions

- Existing Finance, Sales, and CRM data remain the source of truth.
- No schema, migration, backfill, or persistent read model is proposed.
- CRM lookup uses the existing user-to-customer association through its service contract; Finance must not query the CRM repository or entity directly.

## 5. Contracts

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| `GET /api/v1/customer/debts` | `CUSTOMER` | No body or customer ID; no pagination in the initial response | `ApiResponse<CustomerDebtSelfServiceResponse>` | 401 unauthenticated, 403 non-customer role, 404 no linked CRM profile |
| `GET /api/v1/finance/customer-debts/me` | `CUSTOMER` | Existing route retained as an alias; same response and authorization | `ApiResponse<CustomerDebtSelfServiceResponse>` | Same as above |

Response content:

- Summary: `outstandingBalance`, `overdueBalance`, `issuedInvoiceReceivable`, `unallocatedAdvanceAmount`, and `nearestDueDate`.
- Invoice entries: invoice code, associated order ID/code, issue/due date, status, total, verified paid, and outstanding amount.
- Order entries: order code/status, total, verified paid, and outstanding amount.

The customer-facing response should use a dedicated DTO and must not expose the internal `CustomerDebtDetailResponse`, which includes customer profile data, receipt details, and reminder/audit history.

## 6. Concurrency, Transaction and Failure Safety

- Execute as a read-only query using the existing Finance debt projection and calculation.
- There are no writes, locks, retries, or idempotency keys.
- Missing customer linkage must fail explicitly rather than return an empty success-shaped balance.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | A customer receives only their own balance and debt documents | `CustomerDebtServiceImplTest` | Uses the authenticated user's CRM link and UC-24 calculation |
| AC-02 | Customer identity comes from the authenticated principal | `CustomerDebtControllerSecurityTest` | Caller-supplied `customerId` is ignored |
| AC-03 | Non-customer and unauthenticated callers are denied | `CustomerDebtControllerSecurityTest` | 403 and 401 respectively |
| AC-04 | Response excludes internal-only fields and history | Controller JSON assertions and dedicated DTO | No contacts, receipts/proofs, reminders, audit, credit limit, or risk classifications |
| AC-05 | Reading debt does not create profiles or mutate balances | CRM-missing-profile service test; read-only service method | Explicit not-found; no writes |
| AC-06 | Debt summary preserves UC-24 calculation semantics | `CustomerDebtServiceImplTest` | Order/invoice totals are not double-counted |

Targeted verification passed on 2026-11-28 with `mvn -q '-Dsurefire.useManifestOnlyJar=false' '-Dtest=CustomerDebtServiceImplTest,CustomerDebtControllerSecurityTest' test`.

## 8. Open Decisions

| Decision | Owner | Required Before | Status |
|---|---|---|---|
| Approve overriding the existing UC-24 exclusion of a self-service debt portal | Project Owner | Design Review | Approved by user — 2026-11-28 |
| Confirm endpoint path and whether debt-document results need pagination | Technical Lead / Product Owner | Design Review | Resolved: `/api/v1/customer/debts`, no pagination initially; retain existing route as alias |
| Approve the customer-facing summary fields and document visibility policy | Product Owner / Finance Owner | Design Review | Approved by user — 2026-11-28 |

The parent UC-24 specification and active brief are reconciled with this approved scope change. Implementation uses a dedicated customer-facing DTO and reuses the existing UC-24 debt calculation.
