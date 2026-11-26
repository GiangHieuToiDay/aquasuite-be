<!--
HƯỚNG DẪN SỬ DỤNG (xóa phần này trước khi copy vào Report 5)
- File này điền theo đúng mẫu "AQS_Report-5.0_Test Documentation_v1" trong folder V1: Change Log, I.1 Scope, I.2 Test Strategy, I.3 Test Plan, II.1 Test Data, II.2 Test Cases, II.3 Test Reports.
- 5 file Excel đi kèm nằm ở docs/specifications/NAM/report5/ (Report-5.1 đến 5.5, hậu tố _Nam), cùng cấu trúc với mẫu trong folder V1.
- Số liệu UT và IT là KẾT QUẢ THẬT đã chạy ngày 25/11/2026 (BE: ./mvnw test; FE: node --test). ST là 129 test case sinh từ spec (mỗi GBR đã gộp vào luồng tương ứng), kết quả đang là "Pending" trong Excel.
- Chỗ nào có [điền] là số liệu bạn tự điền sau khi chạy thủ công (ST, AT, NFR). Không điền số bạn chưa chạy.
- Mục 3.2 và 3.3 liệt kê lỗi thật đã gặp trong quá trình rà soát; sửa cho khớp nhật ký lỗi của bạn.
- Ngày mốc (3.2 Milestones) là [điền]: lấy theo kế hoạch của nhóm.
-->

# Report 5 – Software Test Documentation (Part: Pham Van Nam)

## Change Log

| Date | A*, M, D | In charge | Change Description |
| :-: | :-: | :-: | :-- |
| 25/11/2026 | A | Pham Van Nam | Created Test Plan, Test Strategy, Test Data and consolidated test results (UT, IT) and prepared system, NFR and acceptance test cases (ST, NFR, AT) for UC-02.1, UC-03.5, UC-06.1, UC-06.2, UC-06.3, UC-14.1, UC-16.1, UC-17.1, UC-17.2, UC-22.6, UC-22.7 and UC-14.2. |

\*A - Added M - Modified D - Deleted

---

# I. Overall Plan

## 1. Scope of Testing

**Features in scope.** This part verifies 12 use cases of AquaSuite (the management platform of Thám Vân Home), owned by Pham Van Nam: UC-02.1 Login; UC-03.5 Manage Role Permissions; UC-06.1 and UC-06.2 Manage Categories; UC-06.3 Manage Brands; UC-14.2 Record Staff-Assisted Payment; UC-14.1 Verify Staff-Recorded Bank Transfer; UC-16.1 Create Goods Receipt from Purchase Order; UC-17.1 Look up Inventory; UC-17.2 Configure Safety Threshold; UC-22.6 View Warranty Tickets; UC-22.7 Process Warranty Ticket.

**Functions and non-functional requirements tested:**
- Functional behaviour of every main, alternative and exception flow and every business rule (GBR) of the use case specifications, including the Vietnamese messages shown on the screen.
- Role-based access control (`@PreAuthorize` roles and permission authorities resolved from the database on every request), session handling with Redis, and the immediate effect of permission changes.
- Idempotency and concurrency: request keys for payments and goods receipts, advisory and row locks, no double stock increase.
- Input validation on forms and API requests.

**Not tested in this part:** other modules and use cases (sales orders, delivery, customer debt, warranty claims and policies of other owners), payment gateway integrations, and formal load testing (only the proposed performance checks in Report-5.4).

**Test stages (levels):**

| Level | In charge | Input / time | Focus | Acceptance criteria |
| :-: | :-: | :-- | :-- | :-- |
| Unit Test (UT) | Pham Van Nam | Source code, 25/11/2026 | Services, mappers, frontend schemas and API clients in isolation | 100% of UT cases pass; every flow with logic has at least one normal and one abnormal case |
| Integration Test (IT) | Pham Van Nam | Running application with PostgreSQL and Redis, 25/11/2026 | HTTP API, security, Flyway schema, concurrency | 100% of IT cases pass; every role restriction is checked |
| System Test (ST) | Pham Van Nam | Specifications, running web application | Every flow and business rule of the use case specification on the screen | All cases executed; no open Critical or Major defect |
| Acceptance Test (AT) | Pham Van Nam with the business user | Business scenarios BF-01 to BF-05 | Complete business scenarios | All scenarios accepted; exploratory findings logged |

**Constraints and assumptions:**
- Automated tests build their own fixtures; system and acceptance tests need the seed data of section II.1.
- Test passwords are never written in documents (`[test_password]`).
- Frontend automated tests cover schemas and API clients, not rendered screens; the screens are covered by ST and AT.

## 2. Test Strategy

### 2.1 Testing Types

| Type | Objective | Technique | Completion criteria |
| :-: | :-- | :-- | :-- |
| Functional Testing | Every flow and business rule works as specified | Equivalence partitioning and boundary value analysis; one case per flow of each specification; the business rules (GBR) are checked inside the flow they apply to | All planned cases executed; no open Critical or Major defect |
| Input Validation Testing | Invalid data is rejected with the specified message | Boundary values (lengths, negative numbers, empty values), form schema tests | All limits in the specifications are checked |
| Security Testing | Only permitted roles can act; no information leak | Role matrix tests with MockMvc, 401 and 403 checks, authority reload tests | Every restricted action is checked for at least one forbidden role |
| Concurrency and Data Integrity Testing | No duplicate or inconsistent data | Idempotent retry tests, repeated request keys, simultaneous requests | Stock and balances stay correct in all cases |
| UI Testing | Screens, dialogs and messages are correct | Manual execution of the specification flows on the web application | All ST and AT scripts executed |

### 2.2 Test Levels

| Type of Tests | Unit Test | Integration Test | System Test | Acceptance Test |
| :-- | :-: | :-: | :-: | :-: |
| Functional Testing | ✓ | ✓ | ✓ | ✓ |
| Input Validation Testing | ✓ | ✓ | ✓ | |
| Security Testing | | ✓ | ✓ | |
| Concurrency and Data Integrity Testing | | ✓ | ✓ | |
| UI Testing | (optional) | | ✓ | ✓ |

### 2.3 Supporting Tools

| Purpose | Tool | Vendor/In-house | Version |
| :-- | :-- | :-: | :-: |
| Backend language and runtime | Java (OpenJDK) | Oracle / Eclipse Adoptium | 21 |
| Backend framework | Spring Boot | Broadcom | 3.5.16 |
| Backend unit and integration tests | JUnit 5 (Jupiter), Mockito, Spring Boot Test, MockMvc, Spring Security Test | Open source | Managed by Spring Boot 3.5.16 |
| Database and migration | PostgreSQL (Docker) and Flyway | PostgreSQL Org / Redgate | Image `postgres:18-alpine`; migrations V1 to V54 |
| Session store | Redis (Docker) | Redis Ltd. | Image `redis:7.2-alpine` |
| Build and test runner (backend) | Apache Maven (wrapper) | Apache | 3.9.9 |
| Frontend framework | React, TypeScript, Vite | Meta / Microsoft / Vite team | React 19.2, TypeScript 6.0, Vite 8.3 |
| Frontend test runner | Node.js built-in test runner | Node.js | 24.22.1 |
| Test documentation | Microsoft Excel workbooks (Report-5.1 to 5.5) | Microsoft | .xlsx |

## 3. Test Plan

### 3.1 Test Environment

| Purpose | Tool | Provider | Version |
| :-- | :-- | :-: | :-: |
| Application server (backend) | Spring Boot (port 8080) | Local host | Java 21 |
| Web client (frontend) | React and Vite (port 5173) | Vite dev server | Node.js 24.22.1 |
| Primary database | PostgreSQL in Docker (port 5432) | Docker | postgres:18-alpine |
| Session cache | Redis in Docker (port 6379) | Docker | redis:7.2-alpine |
| Test runner | Maven wrapper and `node --test` | Apache / Node.js | Maven 3.9.9, Node 24.22.1 |
| Browser for ST and AT | Google Chrome | Google | Latest |

### 3.2 Test Milestones

| Milestone Task | Start Date | End Date |
| :-- | :-: | :-: |
| M1: Test plan and test data | [điền] | [điền] |
| M2: Unit testing (144 cases, BE and FE) | [điền] | 25/11/2026 |
| M3: Integration and security testing (81 cases) | [điền] | 25/11/2026 |
| M4: System testing (129 cases from the specifications) and NFR checks (16) | [điền] | [điền] |
| M5: Acceptance and exploratory testing (5 scripts) | [điền] | [điền] |
| M6: Defect fixing, retest and report consolidation | [điền] | [điền] |

---

# II. Implementation & Schedule

## 1. Test Data

Rules: all automated UT and IT cases are self-contained (they inject their own fixtures with mocks or a PostgreSQL database built by Flyway migrations V1 to V54); no case depends on data left by another case; no case depends on a database sequence ID (fixtures use generated UUIDs and unique codes). For manual system and acceptance tests the passwords are never stored in documents; all password fields use `[test_password]`.

### 1.1 Minimum Test Account

| Username | Role | Password | Account Status | Purpose |
| :-: | :-: | :-: | :-: | :-- |
| owner_test | Shop Owner (SHOP_OWNER) | [test_password] | ACTIVE | Manages role permissions (UC-03.5), categories and brands (UC-06.x), safety thresholds (UC-17.2), goods receipts (UC-16.1), warranty tickets (UC-22.6, UC-22.7); records payments (UC-14.2). |
| accountant_test | Accountant (ACCOUNTANT) | [test_password] | ACTIVE | Verifies bank transfers (UC-14.1), creates goods receipts (UC-16.1), looks up inventory (UC-17.1); read-only on payments (UC-14.2). |
| sales_test | Sales Staff (SALES_STAFF) | [test_password] | ACTIVE | Records staff-assisted payments (UC-14.2); used to check forbidden access to the other use cases. |
| shipper_test | Shipper (SHIPPER) | [test_password] | ACTIVE | Checks forbidden access (403) to management functions. |
| customer_test | Customer (CUSTOMER) | [test_password] | ACTIVE | Logs in to the storefront (UC-02.1) and is redirected to the storefront; checks forbidden access. |
| locked_test | Suspended user (SALES_STAFF) | [test_password] | LOCKED | UC-02.1: an account that is not Active cannot log in. |

Account set-up: `owner_test` (Shop Owner) and `customer_test` (Customer) are created by the development data initializer of the application. `accountant_test`, `sales_test` and `shipper_test` must be created by the Shop Owner in the user account management page before system testing, and `locked_test` is a Sales Staff account whose status is set to Locked after creation. All passwords are kept outside this document.

### 1.2 Minimum Domain Data

| Entity | Count | Required State | Purpose |
| :-: | :-: | :-- | :-- |
| Category | 3 | One root category, one child category, one INACTIVE category; one category that has a child | UC-06.1, UC-06.2: parent-child rules, hide, soft delete, deletion blocked when children exist |
| Brand | 2 | One ACTIVE, one INACTIVE | UC-06.3: unique code and name, hide, soft delete |
| Product and SKU variant | 3 SKUs | Stock records in WH_MAIN with different thresholds (one with minimum 0, one at or below the threshold, one normal) | UC-17.1, UC-17.2: status calculation, low-stock filter, threshold configuration |
| Purchase order | 2 | One "Đã xác nhận" order with 2 lines and remaining quantity above 0; one order already "Hoàn thành" | UC-16.1: full and partial receiving, status checks |
| Sales order and payment | 2 orders | One order with remaining balance above 0; one order with a transfer in "Chờ xác minh" | UC-14.2, UC-14.1: cash, transfer, verification, rejection, exceeding the balance |
| Warranty ticket | 3 | One Chờ tiếp nhận, one Đang xử lý, one Hoàn tất, linked to an active warranty record (one record expired) | UC-22.6, UC-22.7: list, filter, life cycle, expired warning |

## 2. Test Cases

Test cases are kept in five shared master workbooks in the team folder (V1), using the structure of the V1 templates. Each member adds the sheets of the use cases they own; the first sheets were written by Pham Van Nam:

| Report Code | File | Level | Test cases | Status |
| :-: | :-- | :-: | :-: | :-- |
| Report-5.1 | Report-5.1_Unit Test_Master.xlsx | UT | 144 | Executed on 25/11/2026: 144 Passed |
| Report-5.2 | Report-5.2_Integration Test_Master.xlsx | IT (including SecurityTest sheet with 22 cases) | 81 | Executed on 25/11/2026: 81 Passed |
| Report-5.3 | Report-5.3_System Test_FRs_Master.xlsx | ST-FR | 129 | Prepared from the specifications (each business rule is checked inside its flow). Executed on 25/11/2026 at API level: 64 Passed, 0 Failed; 65 screen cases Pending |
| Report-5.4 | Report-5.4_System Test_NFRs_Master.xlsx | ST-NFR (10 security, 6 performance) | 16 | Executed on 25/11/2026 and re-tested on 26/11/2026: 10 Passed, 0 Failed, 6 Not Run (security 7 Passed, 3 Not Run; performance 3 Passed, 3 Not Run) |
| Report-5.5 | Report-5.5_Acceptance Test Scripts_Master.xlsx | AT (5 UAT scripts, exploratory log) | 5 | Prepared; not executed yet (acceptance by the business user) |

Distribution of test cases per use case:

| UC Code | Use Case Name | UT | IT | ST-FR | Total | Key Focus & Business Verification |
| :-: | :-- | :-: | :-: | :-: | :-: | :-- |
| UC-02.1 | Login | 27 | 22 | 6 | 55 | Email and password limits, invalid credentials, account not Active, session creation, token refresh and replay detection, Redis failure handling |
| UC-03.5 | Manage Role Permissions | 45 | 18 | 10 | 73 | Assign and unassign permissions, forbidden for non-owners, fixed roles protected by trigger, frontend access safety |
| UC-06.1, 06.2, 06.3 | Manage Categories and Brands | 7 | 14 | 35 | 56 | CRUD lifecycle, code normalisation, immutable code, parent-child rules, hide, soft delete, permission authorities |
| UC-14.1 | Verify Staff-Recorded Bank Transfer | 9 | 0 | 15 | 24 | Verify within the obligation, exceeding the balance keeps pending, decided only once, rejection reason |
| UC-14.2 | Record Staff-Assisted Payment | 13 | 0 | 13 | 26 | Cash verified immediately, pending transfer, excess rejected, idempotent request key, milestone progress |
| UC-16.1 | Create Goods Receipt from Purchase Order | 19 | 13 | 13 | 45 | Full and partial receiving, remaining quantity, order status, idempotent retry, stock movement, concurrency |
| UC-17.1, 17.2 | Look up Inventory, Configure Safety Threshold | 14 | 14 | 17 | 45 | Search and status calculation, low-stock filter, threshold validation, audit event, role security |
| UC-22.6, 22.7 | View and Process Warranty Tickets | 10 | 0 | 20 | 30 | Life cycle New → In progress → Done / Rejected, required result and reason, expired-warranty warning |
| **Total** | | **144** | **81** | **129** | **354** | Plus 16 NFR cases and 5 acceptance scripts |

Notes: `WarrantyTicketServiceTest` (claim submission) and the frontend warranty form tests belong to other use cases and are not counted. UC-14.2, UC-14.1 and UC-22.6/22.7 have no automated integration test; their API behaviour is covered by ST.

## 3. Test Reports

### 3.1 Summary Table

| Round | Metrics | UT | IT | ST | AT | Total | Note |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-- |
| Round #1 | Total | 144 | 81 | 129 | 5 | 359 | UT and IT run on 25/11/2026; ST 64 cases verified at API level, 65 screen cases pending; AT pending |
| | Passed | 144 | 81 | 64 | 0 | 289 | |
| | Failed | 0 | 0 | 0 | 0 | 0 | |
| | Pending | 0 | 0 | 65 | 5 | 70 | |
| | Pass Rate | 100% | 100% | 49.6% | 0% | 80.5% | Pass Rate = Passed ÷ Total |

### 3.2 Test Levels' Defects

| Metric | Review | UT | IT | ST | AT | Total | Note |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-- |
| Total Defects | 4 | 1 | 0 | 3 | 0 | 8 | See the defect list below |
| Open Defects (Critical) | 0 | 0 | 0 | 0 | 0 | 0 | |
| Open Defects (Major) | 0 | 0 | 0 | 0 | 0 | 0 | All defects closed |

| ID | Level | Defect | Product | Status |
| :-: | :-: | :-- | :-- | :-: |
| D-01 | Review | The goods receipt dialog did not send the `idempotencyKey` that the backend requires | Software Package | Closed |
| D-02 | Review | Some success messages were in English instead of the Vietnamese wording shown on screen | Software Package | Closed |
| D-03 | UT | `DevelopmentDataInitializerTest` still expected 6 canonical roles while the system has 5 | Software Package | Closed |
| D-04 | Review | Duplicate GBR ids in the Business Rules table and a GBR-WT prefix collision with UC-21.2 (renamed to GBR-WP) | Report 3 | Closed |
| D-05 | Review | The design specification lacked `goods_receipts.idempotency_key`, `goods_receipts.request_hash` and `warranty_tickets.expired_acknowledged` | Report 4 | Closed |
| D-06 | ST | Validation messages of the payment recording API are in English ("must not be null", "must be greater than or equal to 0.01", "numeric value out of bounds") instead of Vietnamese (UC-14.2) | Software Package | Closed (Vietnamese messages added to the payment request) |
| D-07 | ST | The specification of UC-14.1 says only the Accountant verifies bank transfers, but the API also allows the Shop Owner | Software Package | Closed (API and menu restricted to the Accountant) |
| D-08 | ST | The login error message returned by the system has no final full stop, while the specification of UC-02.1 shows one | Report 3 | Closed (specification aligned with the system) |

### 3.3 Work Product's Defects

| Product | Review | UT | IT | ST | AT | Total | Note |
| :-: | :-: | :-: | :-: | :-: | :-: | :-: | :-- |
| Software Package | 2 | 1 | 0 | 2 | 0 | 5 | D-01, D-02, D-03, D-06, D-07 |
| Report 1 (Intro) | 0 | 0 | 0 | 0 | 0 | 0 | |
| Report 2 (Plan) | 0 | 0 | 0 | 0 | 0 | 0 | |
| Report 3 (Software Requirement Spec) | 1 | 0 | 0 | 1 | 0 | 2 | D-04, D-08 |
| Report 4 (Software Design Spec) | 1 | 0 | 0 | 0 | 0 | 1 | D-05 |
| Report 5 (Test Documentation) | 0 | 0 | 0 | 0 | 0 | 0 | |
| **Total** | 4 | 1 | 0 | 3 | 0 | 8 | |
