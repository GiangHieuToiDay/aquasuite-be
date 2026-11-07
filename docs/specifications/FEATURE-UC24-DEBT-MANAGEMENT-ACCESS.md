# UC-24 Debt Management Access and Statement Format

## Metadata

- **Work ID**: `FEATURE-UC24-DEBT-PAGE-AUDIT`
- **Feature / Use Case**: UC-24 Manage Customer Debt and Receivables
- **Version**: 1.0
- **Owner**: AquaSuite team
- **Owning Module**: `finance`, `audit`
- **Risk Reasons**: authorization, money, public HTTP contract
- **Design Review**: APPROVED
- **Decision Owner**: Store Owner / Technical Lead

## 1. Goal and Scope

### Problem and Desired Outcome

The debt-management workspace must be available only to the Store Owner and Accountant without breaking POS order creation by Sales Staff. The exported document must be a clear, printable Vietnamese debt statement, visually structured like a sales invoice while remaining distinct from a legal tax invoice.

### In Scope

- Restrict the UC-24 dashboard, detail and blacklist-status reads to `SHOP_OWNER` and `ACCOUNTANT`.
- Add a minimal sales-order credit summary for POS.
- Keep aggregate `CUSTOMER` activity visible only to the Store Owner and Accountant as specified by `FEATURE-AUDIT-ACTIVITY-PANELS`.
- Generate an internal debt statement with customer details, issued-invoice balances, verified receipts, totals and signature areas.

### Out of Scope

- Issuing or replacing electronic tax invoices (UC-25/UC-26).
- Changing the existing credit-authorization decision or creating a new credit table.
- Adding store identity, address, tax registration or signature images not present in the current configuration/data model.

## 2. Actors and Security

| Actor | Allowed Operations | Required Role | Data Restrictions |
|---|---|---|---|
| Store Owner | View/manage UC-24 dashboard and detail, export/email statement, block or restore | `SHOP_OWNER` | Full UC-24 management projection |
| Accountant | View dashboard/detail, export/email statement and customer activity | `ACCOUNTANT` | Full UC-24 read projection; no block/restore |
| Sales Staff | Read POS credit summary and submit credit check | `SALES_STAFF` | Minimal summary only; no invoice, receipt, reminder or audit history |

Backend authorization is authoritative. The frontend route/menu guard only prevents accidental navigation.

## 3. Business Rules

1. Sales Staff cannot read the UC-24 management list, detail or blacklist-status endpoints.
2. POS receives only the customer identity, customer status, credit term, credit limit, outstanding amount, overdue amount, available credit and calculated debt status.
3. A debt statement is an internal accounting document. It must state that it is not a replacement for an electronic invoice or legally valid payment document.
4. Monetary amounts are formatted in VND without fractional digits.
5. The PDF embeds a Vietnamese-capable font. The Docker runtime installs DejaVu Sans; non-Docker deployments may set `AQUASUITE_PDF_FONT_PATH` to an equivalent TTF.

## 4. Data and Migration Decisions

- No schema, data backfill or migration change.
- The existing calculated customer-debt projection remains the source for both views.
- The POS summary is a read model generated from existing customer, sales and finance data; it stores nothing.

## 5. Contracts

### HTTP API

| Method/Path | Authorization | Request/Validation | Success | Errors |
|---|---|---|---|---|
| `GET /api/v1/finance/customer-debts` | `SHOP_OWNER`, `ACCOUNTANT` | Existing filters, additive `sortBy`, `direction`, paging | `ApiResponse<CustomerDebtPageResponse>` | 403 for Sales Staff/other roles |
| `GET /api/v1/finance/customer-debts/{customerId}` | `SHOP_OWNER`, `ACCOUNTANT` | UUID path | `ApiResponse<CustomerDebtDetailResponse>` | 403 for Sales Staff/other roles |
| `GET /api/v1/finance/credit-control/customers/{customerId}/summary` | `SALES_STAFF`, `ACCOUNTANT`, `SHOP_OWNER` | UUID path | `ApiResponse<CustomerCreditSummaryResponse>` | Existing customer-not-found response; no document/history fields |
| `GET /api/v1/finance/customer-debts/{customerId}/statement` | `SHOP_OWNER`, `ACCOUNTANT` | UUID path | PDF attachment `sao-ke-cong-no-{customerCode}.pdf` | Existing finance errors |

## 6. Concurrency, Transaction and Failure Safety

- The new POS summary uses the same read-time debt calculation as credit checks; it performs no write or reservation.
- Existing actual credit authorization continues to use `CreditControlApi` locking/transaction boundaries. A displayed summary is informational and never authorizes an order by itself.
- Statement export is read-only except for the existing audit event recording the export. Generation failure returns an error and does not mutate receivables.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Sales Staff is denied UC-24 dashboard/detail | MVC security test | HTTP 403 |
| AC-02 | Owner and Accountant can read UC-24 dashboard | MVC security test | HTTP 200 |
| AC-03 | Sales POS can read minimal credit summary | MVC security test | HTTP 200 without invoices/reminder history |
| AC-04 | Statement has Vietnamese text, tables, totals and signatures | PDF unit test plus rendered A4 preview | Readable one-page PDF for fixture data |
| AC-05 | Frontend POS uses the new endpoint and debt pages are route/menu-guarded | frontend test/lint/build | Build succeeds |

## 8. Open Decisions

None.
