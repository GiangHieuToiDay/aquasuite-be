# Test Sync Checkpoint (`develop-update`)

- **Cáº­p nháº­t láº§n cuá»‘i:** 2026-11-28 01:21 (+07:00)
- **NhÃ¡nh má»¥c tiÃªu:** `develop-update` (BE: `d:\FULearning\Fall2026\SEP490\aquasuite-be`, Ä‘Ã£ `git pull` má»›i nháº¥t)
- **Pháº¡m vi UC:** `UC-05, UC-10, UC-24, UC-26, UC-63, UC-44, UC-48`
- **TÃ i liá»‡u test má»¥c tiÃªu (Google Drive folder `14hh3GRN8E7ui0W4OV7zisU5RhOxQOjgZ`):**
  - `Report-5.1_Unit Test.xlsx` (`1Mfrd8iupu1hVGciB_nK6I_ZflWa0RwM8`)
  - `Report-5.2_Integration Test.xlsx` (`1NQv8w39vynNKn5AsgyBeLP3F9xphKenE`)
  - `Report-5.3_System Test_FRs.xlsx` (`1dxk-WMI8ViZIXaQiy5h4XSRTtTfjXXuO`)
  - `Report-5.5_Acceptance Test Scripts.xlsx` (`1gdza0TZKzuD-Dy3mt145ngtwVw1KnHrv`)
  - *(KhÃ´ng chá»‰nh sá»­a `Report-5.0_Test Documentation.docx` vÃ  cÃ¡c bÃ¡o cÃ¡o tá»•ng cá»§a nhÃ³m)*

## Tiáº¿n Ä‘á»™ Giai Ä‘oáº¡n
- [x] **Giai Ä‘oáº¡n 1:** Äá»c code nhÃ¡nh `develop-update` (`codebase-analyst`) & Äá»‘i chiáº¿u tÃ i liá»‡u test cÅ© (`test-doc-auditor`)
  - [x] UC-05 (Account & Status Management)
  - [x] UC-10 (Variant & Pricing Management)
  - [x] UC-24 (Customer Debt & Receivables)
  - [x] UC-26 (Order Modification / Cancellation Requests)
  - [x] UC-63 (Payment Allocation)
  - [x] UC-44 (Warranty Policies)
  - [x] UC-48 (Warranty Lookup / Records)
- [x] **Giai Ä‘oáº¡n 2:** Cháº¡y kiá»ƒm thá»­ xÃ¡c minh trÃªn nhÃ¡nh `develop-update` (`test-runner`)
- [ ] **Giai Ä‘oáº¡n 3:** Cáº­p nháº­t tÃ i liá»‡u test riÃªng pháº§n UC trÃªn Drive (`test-doc-writer`)

## TÃ³m táº¯t Ä‘á»‘i chiáº¿u Giai Ä‘oáº¡n 1 (Gap Analysis: Docs vs `develop-update`)
- **Kiáº¿n trÃºc chung:** Refactor tá»« `module/<name>` sang 3-layer (`controller/`, `service/`, `repository/`, `dto/`, `entity/`, `enums/`, `event/`, `exception/`, `listener/`).
- **UC-05:**
  - `5.1`: 12 giá»¯ nguyÃªn, 3 cáº§n sá»­a (`UT-UC05-02`: `IAM_ACC_006`â†’`IAM_ACC_005`; `UT-UC05-03`: `IAM_ACC_005`â†’`IAM_ACC_006`; `UT-UC05-05`: `IAM_ACC_002`â†’`MSG25`), cáº§n thÃªm `IAM_ACC_007` (password UTF-8 <=72B, no whitespace, complexity), `IAM_ACC_001/003/004`, `GET /users/shippers`, `sortBy` whitelist (`SYS_002`).
  - `5.2`: 17 giá»¯ nguyÃªn, cáº§n thÃªm `GET /users/shippers`, Bean Validation má»›i (`@Size(max=72)`, `avatarUrl`, `sortBy`).
  - `5.3`: 11 giá»¯ nguyÃªn, 1 cáº§n sá»­a (`ST-UC05-002`: `role=SALES_STAFF` â†’ `roleId` UUID), cáº§n thÃªm password rule má»›i.
  - `5.5`: Giá»¯ nguyÃªn 100%.
- **UC-10 (Breaking RBAC commit `4305760`):**
  - `GET /api/v1/catalog/products/{productId}/variants` & `/variants/stats` thu háº¹p thÃ nh **chá»‰ `SHOP_OWNER`** (`SALES_STAFF`, `ACCOUNTANT` nháº­n `403`).
  - `5.2`: Sá»­a `IT-UC10-04`, `SEC-UC10-03`; xem xÃ©t xÃ³a/viáº¿t láº¡i `IT-UC10-01/02` (`module.iam.internal` Ä‘Ã£ bá» sau refactor 3-layer); thÃªm validation má»›i (`@Digits`, `@Size`) & `SKU_008`, `SKU_010..013`.
  - `5.3`: Sá»­a `ST-UC10-010`, `ST-UC10-011`, `ST-UC10-013`; thÃªm `SKU_013`, `SKU_008`.
  - `5.5`: Sá»­a `UAT-05` vÃ  `Exploratory` dÃ²ng 3.
- **UC-24:**
  - `5.1`: 51 giá»¯ nguyÃªn, sá»­a `UT-UC24-12` (`FIN_DEBT_004` â†’ `DEBT_002`), thÃªm `DEBT_001/004/005`, `getBlacklistStatus`.
  - `5.2`: 28 giá»¯ nguyÃªn, thÃªm `GET /blacklist-status`, Bean Validation `DebtCallResultRequest` & `ReasonRequest`, `sortBy` whitelist.
  - `5.3`: Sá»­a `ST-UC24-001` (`sortBy=overdueBalance` ngoÃ i whitelist), `ST-UC24-003` (`/statement.pdf` â†’ `/statement`), `ST-UC24-004` (`/statement-email` â†’ `/statement/email`).
  - `5.5`: Giá»¯ nguyÃªn 100%.
- **UC-26 (`OrderModificationRequestController` gá»™p vÃ o `SalesOrderController`):**
  - `5.1`: 29 giá»¯ nguyÃªn, thÃªm `ORDER_MOD_005/006/007/009/010/013/014`, `recordTaxResult` khi refund chÆ°a `VERIFIED`.
  - `5.2`: 22 giá»¯ nguyÃªn (cáº­p nháº­t tÃªn controller náº¿u cáº§n), sá»­a `SEC-UC26-06` (`/tax-result` chá»‰ `ACCOUNTANT`, bá» `SHOP_OWNER`), thÃªm case `SHOP_OWNER` 403 & Bean Validation.
  - `5.3`, `5.5`: Giá»¯ nguyÃªn 100%.
- **UC-63 (`PaymentAllocationController` gá»™p vÃ o `PaymentController`):**
  - `5.1`: 30 giá»¯ nguyÃªn, sá»­a `UT-UC63-12` (`FIN_ALLOC_008` â†’ `ALC_011`), lÆ°u Ã½ `providerInvoiceId`+`signedAt` á»Ÿ `UT-UC63-04`, thÃªm `ALC_004/005/012`.
  - `5.2`: Cáº­p nháº­t tÃªn `PaymentController` á»Ÿ `IT-UC63-01..05` & `SEC-UC63-01..04`; thÃªm `/api/v1/finance/payment-verifications/**` (`ACCOUNTANT` only, `ff3eed4`) vÃ  `GET /unallocated` (`view`, `sortBy`).
  - `5.3`, `5.5`: Giá»¯ nguyÃªn 100%.
- **UC-44:**
  - `5.1`: 12 giá»¯ nguyÃªn.
  - `5.2`: 14 giá»¯ nguyÃªn, thÃªm biÃªn `@Max(600)` trÃªn `warrantyPeriodMonths`, `@Size`, `sortBy`.
  - `5.3`: Sá»­a `ST-UC44-001/002` (`WP-...` â†’ `POL-%05d`), thÃªm `@Max(600)`.
  - `5.5`: Giá»¯ nguyÃªn 100%.
- **UC-48 (`WarrantyLookupController` & `CustomerWarrantyController` gá»™p vÃ o `WarrantyRecordController`):**
  - `5.1`: Sá»­a tÃªn class á»Ÿ `UT-UC48-01..05` (`WarrantyLookupServiceImpl` â†’ `WarrantyRecordServiceImpl`), thÃªm UT cho `getMyRecords/getMyRecordDetail/searchRecords/getRecordDetail`, `WarrantyDisplayStatus.CLAIMED` dÃ¹ng `WarrantyTicket` (`NEW`/`IN_PROGRESS`) thay `TechnicalJob`, `SVD_008/009/019`.
  - `5.2`: Cáº­p nháº­t tÃªn class á»Ÿ `IT-UC48-01..02` & `SEC-UC48-01..12` thÃ nh `WarrantyRecordController`; thÃªm IT/Security cho `GET /api/v1/warranty-records/**` (`SHOP_OWNER` & `SALES_STAFF` 200; **`TECHNICAL_STAFF` 403** theo `d5e7b7b`).
  - `5.3`: Sá»­a `ST-UC48-009` tÃªn controller thÃ nh `WarrantyRecordController`; thÃªm ST cho staff lookup & `TECHNICAL_STAFF` 403 & `CLAIMED` qua `WarrantyTicket`.
  - `5.5`: Giá»¯ nguyÃªn 100%.

## Káº¿t quáº£ Giai Ä‘oáº¡n 2 (Test Runner & Verifier)
- **ÄÃ£ kiá»ƒm tra & verify thÃ nh cÃ´ng:**
  - `ThreeLayerArchitectureTest`: 7/7 tests PASS (11.69s)
  - `UserAccountServiceImplTest`: 11/11 tests PASS (0.356s)
  - `CustomerDebtServiceImplTest`: 20/20 tests PASS (0.550s)
  - `CreditControlApiImplTest`: 1/1 test PASS (0.017s)
  - `StatementPdfServiceTest`: 1/1 test PASS (0.515s)
  - `OrderModificationRequestServiceImplTest`: 20/20 tests PASS (0.642s)
  - `PaymentAllocationServiceImplTest`: 21/21 tests PASS (0.310s)
  - `PaymentAllocationAdjustmentApiImplTest`: 2 tests (2 skipped)
  - `ElectronicInvoiceAllocationListenerTest`: 1/1 test PASS (0.012s)
  - `PaymentVerificationServiceTest`: 6/6 tests PASS (0.064s)
  - `WarrantyPolicyServiceImplTest`: 9/9 tests PASS (0.187s)
  - `WarrantyLookupServiceImplTest`: 5/5 tests PASS (0.041s)
  - `WarrantyRecordServiceTest`: 10/10 tests PASS (0.116s)
  - `VariantControllerTest`: 8/8 tests PASS (BUILD SUCCESS 35.370s)
    * `salesStaffIsForbiddenFromListAndStats`: 403 AUTH_002 (PASS)
    * `shopOwnerReceivesFullCostPriceInListAndStats`: 200 OK (PASS)
    * `accountantIsForbiddenFromListAndStats`: 403 AUTH_002 (PASS)
  - CÃ¡c Controller/Security tests liÃªn quan (UserAccount, ShipperLookup, CustomerDebt, PaymentAllocation, OrderModification, WarrantyPolicy, CustomerWarranty, WarrantyLookup, WarrantyEligibility) Ä‘á»u PASS 100%.



## Kết quả Giai đoạn 3 (Test Doc Writer — 2026-11-28 03:17 +07:00)

### Files đã cập nhật và lưu tại C:\Users\Hao\Downloads\

| File | Thay đổi | Output |
|---|---|---|
| Report-5.1_Unit Test_updated.xlsx | 10 changes: UC05 error codes swap (IAM_ACC_005↔006), MSG25, FIN_DEBT_004→DEBT_002, FIN_ALLOC_008→ALC_011, WarrantyLookupServiceImpl→WarrantyRecordServiceImpl (5 cells) | OK [236KB] |
| Report-5.2_Integration Test_updated.xlsx | 42 changes: PaymentAllocationController→PaymentController, CustomerWarrantyController/WarrantyLookupController→WarrantyRecordController, IT-UC10-04 (200→403), SEC-UC10-03 (200→403) | OK [272KB] |
| Report-5.3_System Test_FRs_updated.xlsx | 10 changes: overdueBalance→outstandingBalance, /statement.pdf→/statement, /statement-email→/statement/email, role=SALES_STAFF→roleId=UUID, ST-UC10-010/011 expected→403, ST-UC10-013 Actor fix, ST-UC44 policy code, WarrantyRecordController | OK [79KB] |
| Report-5.5_Acceptance Test Scripts_updated.xlsx | 6 changes: UAT-05 E6/F6 updated with 403 note, Exploratory Row4 updated for SALES_STAFF 403 | OK [16KB] |

> Note: test-doc-writer (claude-sonnet-4-6) exhausted quota (429, resets 167h). Coordinator completed Phase 3 directly.

| Report-5.0_Test Documentation_updated.docx | 9 changes: Change Log (28/11/2026), Scope UC-10/UC-26/UC-48, Table 5 (sales_staff, technical_staff), Table 8 (UC-10, UC-48, Cross-Cutting) | OK [60KB] |
| Report-5.4_System Test_NFRs_updated.xlsx | 4 changes: TC-SEC-005 E6+F6 (403 commit 4305760), TC-SEC-013 J13 (PaymentControllerSecurityTest), TC-SEC-014 J15 (WarrantyRecordControllerSecurityTest) | OK [13KB] |
