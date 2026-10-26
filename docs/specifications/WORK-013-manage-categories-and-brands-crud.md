# WORK-013 — Manage Categories and Brands CRUD

## Metadata

- **Work ID**: `WORK-013`
- **Feature / Use Case**: Manage Categories and Brands CRUD
- **Version**: `0.2.0`
- **Owner**: Product Owner / Tech Lead / Security Owner
- **Owning Module**: `catalog`
- **Risk Reasons**: `public HTTP contract | schema | authorization | hierarchy concurrency`
- **Design Review**: `PENDING`
- **Decision Owner**: Owner decisions supplied; CODEX_REVIEW remains responsible for verdict

## 1. Goal and Scope

### Problem and Desired Outcome

Cung cấp CRUD HTTP cho Category và Brand thuộc `com.aquasuite.ims.module.catalog`, với stable business codes phục vụ Product Excel Import tương lai, schema PostgreSQL chính xác, hierarchy an toàn khi concurrent writes và authorization theo permission hiện hành.

### In Scope

- Sở hữu `categories`, `brands`, Category hierarchy, Category/Brand CRUD, permissions, HTTP API, `CategoryMapper`, `BrandMapper` và Bruno requests sau implementation.
- Tạo internal layer-based packages; không tạo public `catalog/api` khi chưa có inter-module consumer.
- Forward Flyway migration kế tiếp sau latest migration được xác minh tại implementation.
- Flat paginated list, search, status filter, allow-listed sort, stable errors và verification matrix.

### Out of Scope

- `products` table, Product entity/repository/service/controller/CRUD/Excel import/mapper/skeleton.
- `products.category_id`, `products.brand_id` và mọi Product FK/dependency check.
- Recursive tree, public Catalog Java API/Event, soft delete, optimistic locking và JWT/Redis changes.

## 2. Actors and Security

| Actor | Allowed Operations | Required Permission | Data Restrictions |
|---|---|---|---|
| Category reader | List/get | `CATALOG_CATEGORY_READ` | Dữ liệu không nhạy cảm |
| Category creator | Create | `CATALOG_CATEGORY_CREATE` | Code/name/hierarchy constraints |
| Category updater | Update | `CATALOG_CATEGORY_UPDATE` | Không đổi code |
| Category deleter | Delete | `CATALOG_CATEGORY_DELETE` | Không có child |
| Brand reader | List/get | `CATALOG_BRAND_READ` | Dữ liệu không nhạy cảm |
| Brand creator | Create | `CATALOG_BRAND_CREATE` | Code/name constraints |
| Brand updater | Update | `CATALOG_BRAND_UPDATE` | Không đổi code |
| Brand deleter | Delete | `CATALOG_BRAND_DELETE` | Hiện không có Product FK |

- Endpoint dùng `hasAuthority(...)`; migration gán tám permissions cho canonical `SHOP_OWNER`.
- `AccessAuthenticationConverter`/`CurrentAuthoritiesService` tải authorities từ PostgreSQL trên mỗi authenticated request. Assignment/removal có hiệu lực từ request kế tiếp với token/session còn hợp lệ; không cần logout/login, refresh/reissue token hay revoke Redis session.
- Không đổi JWT/Redis semantics. Bruno lấy token từ environment/runtime variable, không hardcode.

## 3. Business Rules

### Code Normalization

1. Reject `null`.
2. Trim leading/trailing whitespace.
3. Uppercase bằng `Locale.ROOT`.
4. Validate length `1..50`.
5. Validate `^[A-Z0-9][A-Z0-9_]*$`.
6. Persist normalized value.

- Hợp lệ: `109`, `CAT_109`, `TOTO`, `INAX`, `CADISUN`, `" CAT_109 "`, `" 109 "`, `" toto "`; ba input cuối persist thành `CAT_109`, `109`, `TOTO`.
- Không hợp lệ: `CAT 109`, `CAT-109`, `CÁT_109`, `_CAT_109`, blank/null.
- Code bắt buộc khi create, globally unique case-insensitive sau trim, immutable, không có trong update request, độc lập name/parent.
- Database lưu canonical uppercase; direct SQL có surrounding whitespace bị CHECK từ chối.

### Other Rules

- Name trim, bắt buộc, max 100. Category name unique case-insensitive trong cùng parent; root cũng unique; Brand name global unique. Name mutable, không đổi code.
- Description `TEXT`, nullable, trim; blank thành `null`; API max 5000.
- Status `ACTIVE|INACTIVE`, default `ACTIVE`, DB CHECK; không `DELETED`.
- Parent inactive có thể có active child; không cascade status.
- Flat Category list trả `parentId`; cấm self-parent/cycle. Corrupt hierarchy làm mutation bị từ chối, không tự sửa.
- Hard delete; Category có child bị chặn; Brand hiện delete được; không cascade.

## 4. Data and Migration Decisions

### Exact Columns

| Table/Column | Definition | Behavior |
|---|---|---|
| `categories.category_id` | `UUID NOT NULL` | Application-generated; PK |
| `categories.category_code` | `VARCHAR(50) NOT NULL` | Canonical stable key |
| `categories.category_name` | `VARCHAR(100) NOT NULL` | Trimmed, mutable |
| `categories.parent_id` | `UUID NULL` | Self-reference |
| `categories.description` | `TEXT NULL` | API max 5000 |
| `categories.status` | `VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'` | `ACTIVE|INACTIVE` |
| `categories.created_at` | `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` | DB-managed |
| `categories.updated_at` | `TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP` | DB-managed trigger |
| `brands.brand_id` | `UUID NOT NULL` | Application-generated; PK |
| `brands.brand_code` | `VARCHAR(50) NOT NULL` | Canonical stable key |
| `brands.brand_name` | `VARCHAR(100) NOT NULL` | Trimmed, mutable |
| `brands.description` | `TEXT NULL` | API max 5000 |
| `brands.status` | `VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'` | `ACTIVE|INACTIVE` |

Brand không có timestamps; không có version column.

### Exact Database Objects

| Object Type | Exact Name | Definition/Purpose |
|---|---|---|
| PK | `pk_categories` | `categories(category_id)` |
| PK | `pk_brands` | `brands(brand_id)` |
| FK | `fk_categories_parent` | `parent_id -> category_id ON DELETE RESTRICT` |
| CHECK | `chk_categories_code_format` | trim/canonical uppercase, length 1–50, approved regex |
| CHECK | `chk_categories_name_not_blank` | trimmed/non-empty name |
| CHECK | `chk_categories_status` | `ACTIVE|INACTIVE` |
| CHECK | `chk_categories_not_self_parent` | null or different ID |
| CHECK | `chk_brands_code_format` | Brand canonical code rule |
| CHECK | `chk_brands_name_not_blank` | trimmed/non-empty name |
| CHECK | `chk_brands_status` | `ACTIVE|INACTIVE` |
| Unique index | `uq_categories_code_lower` | `lower(btrim(category_code))` |
| Unique index | `uq_categories_root_name_lower` | `lower(btrim(category_name)) WHERE parent_id IS NULL` |
| Unique index | `uq_categories_parent_name_lower` | `(parent_id,lower(btrim(category_name))) WHERE parent_id IS NOT NULL` |
| Unique index | `uq_brands_code_lower` | `lower(btrim(brand_code))` |
| Unique index | `uq_brands_name_lower` | `lower(btrim(brand_name))` |
| Lookup index | `idx_categories_parent_id` | `categories(parent_id)` |
| Filter index | `idx_categories_status` | `categories(status)` |
| Filter index | `idx_brands_status` | `brands(status)` |
| Function | `catalog_set_categories_updated_at()` | Preserve `OLD.created_at`; set `NEW.updated_at=CURRENT_TIMESTAMP`; return `NEW` |
| Trigger | `trg_categories_set_updated_at` | `BEFORE UPDATE ON categories FOR EACH ROW` |

- Insert uses timestamp defaults; direct SQL updates also fire trigger. JPA treats Category timestamps as DB-managed.
- Migration filename uses version after latest verified baseline; do not hardcode V3/V4 or edit applied migrations.
- Preflight fails on incompatible objects/data/code/UUID; no overwrite or partial mutation.

### Permission Provisioning

| Permission Code | Stable UUID | Description |
|---|---|---|
| `CATALOG_CATEGORY_READ` | `20000000-0000-4000-8000-000000000001` | `Read catalog categories` |
| `CATALOG_CATEGORY_CREATE` | `20000000-0000-4000-8000-000000000002` | `Create catalog categories` |
| `CATALOG_CATEGORY_UPDATE` | `20000000-0000-4000-8000-000000000003` | `Update catalog categories` |
| `CATALOG_CATEGORY_DELETE` | `20000000-0000-4000-8000-000000000004` | `Delete catalog categories` |
| `CATALOG_BRAND_READ` | `20000000-0000-4000-8000-000000000005` | `Read catalog brands` |
| `CATALOG_BRAND_CREATE` | `20000000-0000-4000-8000-000000000006` | `Create catalog brands` |
| `CATALOG_BRAND_UPDATE` | `20000000-0000-4000-8000-000000000007` | `Update catalog brands` |
| `CATALOG_BRAND_DELETE` | `20000000-0000-4000-8000-000000000008` | `Delete catalog brands` |

- Preflight each code/UUID. Compatible existing row is retained; missing row inserted; incompatible code/UUID/description fails transaction.
- Verify compatible canonical `SHOP_OWNER`; missing/incompatible fails. Insert missing `role_permissions`, retain existing assignments, do not delete other data or alter other roles.

### Product Migration Coordination

1. Merge WORK-013 into shared baseline before Product UC.
2. Product branch rebase/merge new baseline and re-check latest Flyway version.
3. Product UC chooses next version; branches must not share a Flyway version.
4. Product UC owns `products.category_id UUID NOT NULL`, `products.brand_id UUID NULL`, exact FKs using `RESTRICT/NO ACTION`, and reference conflict tests.
5. Product UC must not edit applied WORK-013 migration.

## 5. Contracts

### HTTP API

| Method/Path | Permission | Request | Success |
|---|---|---|---|
| `POST /api/v1/catalog/categories` | `CATALOG_CATEGORY_CREATE` | `code,name` required; `description,status,parentId` optional | `201 ApiResponse<CategoryResponse>` |
| `GET /api/v1/catalog/categories` | `CATALOG_CATEGORY_READ` | List query | `200 ApiResponse<CategoryPageResponse>` |
| `GET /api/v1/catalog/categories/options` | `CATALOG_CATEGORY_READ` | None | `200 ApiResponse<List<CategoryBriefResponse>>` |
| `GET /api/v1/catalog/categories/{categoryId}` | `CATALOG_CATEGORY_READ` | UUID | `200 ApiResponse<CategoryResponse>` |
| `PUT /api/v1/catalog/categories/{categoryId}` | `CATALOG_CATEGORY_UPDATE` | `name,status` required; `description,parentId` nullable; no code | `200 ApiResponse<CategoryResponse>` |
| `DELETE /api/v1/catalog/categories/{categoryId}` | `CATALOG_CATEGORY_DELETE` | UUID | `200 ApiResponse<Void>`; null data omitted |
| `POST /api/v1/catalog/brands` | `CATALOG_BRAND_CREATE` | `code,name` required; `description,status` optional | `201 ApiResponse<BrandResponse>` |
| `GET /api/v1/catalog/brands` | `CATALOG_BRAND_READ` | List query | `200 ApiResponse<BrandPageResponse>` |
| `GET /api/v1/catalog/brands/options` | `CATALOG_BRAND_READ` | None | `200 ApiResponse<List<BrandBriefResponse>>` |
| `GET /api/v1/catalog/brands/{brandId}` | `CATALOG_BRAND_READ` | UUID | `200 ApiResponse<BrandResponse>` |
| `PUT /api/v1/catalog/brands/{brandId}` | `CATALOG_BRAND_UPDATE` | `name,status` required; `description` nullable; no code | `200 ApiResponse<BrandResponse>` |
| `DELETE /api/v1/catalog/brands/{brandId}` | `CATALOG_BRAND_DELETE` | UUID | `200 ApiResponse<Void>`; null data omitted |

- Create omitted status defaults ACTIVE; PUT replaces all mutable fields. Unknown JSON fields remain ignored per current baseline; no global ObjectMapper change.
- Responses: Category `id,code,name,description,status,parentId,createdAt,updatedAt`; Brand `id,code,name,description,status`; pages `items,page,size,totalElements,totalPages`.

### Pagination, Search, Filter and Sort

| Resource | Query Parameters | Defaults | Allowed Sort Fields |
|---|---|---|---|
| Category | `page,size,search?,status?,sortBy,direction` | `0,20,null,null,code,asc` | `code,name,status,createdAt,updatedAt` |
| Brand | `page,size,search?,status?,sortBy,direction` | `0,20,null,null,code,asc` | `code,name,status` |

- No parent filter in V1. `page>=0`, `size=1..100`, direction `asc|desc`; invalid page/size/sort/direction/status filter => `400 SYS_002`.
- Search is trimmed, blank means absent, case-insensitive contains over code and name.
- Default orders: `categoryCode ASC, categoryId ASC`; `brandCode ASC, brandId ASC`. Client primary sort uses requested direction and always ID ASC tie-breaker.

### MapStruct Contract

- Only `CategoryMapper`/`BrandMapper`; `componentModel=SPRING`, `injectionStrategy=CONSTRUCTOR`, `unmappedTargetPolicy=ERROR`; services inject interfaces.
- Category: `categoryId->id`, `categoryCode->code`, `categoryName->name`, same description/status, `parent.categoryId->parentId`, same timestamps.
- Brand: `brandId->id`, `brandCode->code`, `brandName->name`, same description/status.
- Service-facing response/page methods reject null via `Objects.requireNonNull`; nullable description/parent remain null; empty page returns empty items and exact metadata.
- No repository/service/transaction/business logic/mutation in mapper. Generated source stays in build output and is never directly injected or Git-tracked.

### Error Catalog

| Enum | Code | HTTP | Condition |
|---|---|---|---|
| `CATEGORY_NOT_FOUND` | `CAT_001` | 404 | Category missing |
| `BRAND_NOT_FOUND` | `CAT_002` | 404 | Brand missing |
| `INVALID_CATEGORY_CODE` | `CAT_003` | 400 | Invalid Category code |
| `INVALID_BRAND_CODE` | `CAT_004` | 400 | Invalid Brand code |
| `CATEGORY_CODE_DUPLICATE` | `CAT_005` | 409 | Duplicate Category code |
| `BRAND_CODE_DUPLICATE` | `CAT_006` | 409 | Duplicate Brand code |
| `CATEGORY_NAME_DUPLICATE` | `CAT_007` | 409 | Duplicate sibling/root name |
| `BRAND_NAME_DUPLICATE` | `CAT_008` | 409 | Duplicate Brand name |
| `CATEGORY_PARENT_NOT_FOUND` | `CAT_009` | 404 | Parent missing |
| `CATEGORY_SELF_PARENT` | `CAT_010` | 400 | Self-parent |
| `CATEGORY_CYCLE` | `CAT_011` | 409 | New cycle |
| `CATEGORY_HIERARCHY_CONFLICT` | `CAT_012` | 409 | Existing corrupt cycle/chain |
| `CATEGORY_HAS_CHILDREN` | `CAT_013` | 409 | Delete parent with child |
| `CATEGORY_STATUS_INVALID` | `CAT_014` | 400 | Invalid Category status |
| `BRAND_STATUS_INVALID` | `CAT_015` | 400 | Invalid Brand status |
| `CATEGORY_REFERENCED` | `CAT_016` | 409 | Reserved until Product FK exists |
| `BRAND_REFERENCED` | `CAT_017` | 409 | Reserved until Product FK exists |

### Constraint and Index Mapping

| Operation | Structured Object | Error | Fallback |
|---|---|---|---|
| Category/Brand create | `chk_categories_code_format` / `chk_brands_code_format` | `CAT_003` / `CAT_004` | `DB_001` |
| Category/Brand write | `uq_categories_code_lower` / `uq_brands_code_lower` | `CAT_005` / `CAT_006` | `DB_001` |
| Category write | `uq_categories_root_name_lower` / `uq_categories_parent_name_lower` | `CAT_007` | `DB_001` |
| Brand write | `uq_brands_name_lower` | `CAT_008` | `DB_001` |
| Category parent write | `fk_categories_parent` | `CAT_009` | `DB_001` |
| Category write | `chk_categories_not_self_parent` | `CAT_010` | `DB_001` |
| Category delete | `fk_categories_parent` | `CAT_013` | `DB_001` |
| Status write | `chk_categories_status` / `chk_brands_status` | `CAT_014` / `CAT_015` | `DB_001` |
| Blank name direct SQL | `chk_categories_name_not_blank` / `chk_brands_name_not_blank` | `SYS_003` | `DB_001` |

- Extract only Hibernate `ConstraintViolationException#getConstraintName()` or PostgreSQL `PSQLException` server error constraint. Never substring-match messages. Null/unknown metadata and unrelated integrity errors => `DB_001`.
- `CAT_016/CAT_017` are unreachable/reserved; Product UC later adds exact FK mapping.

## 6. Concurrency, Transaction and Failure Safety

- Reads use `@Transactional(readOnly=true)` without lock. Brand operations never take Category lock.
- Category create/update/delete use `@Transactional`; repository native call `SELECT pg_advisory_xact_lock(1128354892, 1)` is first statement before every read/validation/mutation.
- No Java hash/dynamic/session lock or optimistic lock; transaction completion/rollback releases it.
- Cycle algorithm traverses from prospective parent via repository projection `(categoryId,parentId)`, tracking visited UUIDs. Encounter current category => `CAT_011`; repeated/missing ancestor or exceeding `current row count + 1` => `CAT_012`; null parent terminates.
- Root create also locks. Delete locks, loads target, checks child, then deletes/flushes. Serialization prevents reciprocal reparent and create/move-child vs delete-parent races.
- Unique indexes are final race guard; failure rolls back transaction.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | Exact forward migration/permissions | Testcontainers real-current + fresh tests | No partial mutation |
| AC-02 | Codes canonical and immutable | API/service/direct SQL | Approved normalized; invalid rejected |
| AC-03 | Category/Brand CRUD | Integration tests | Exact contract |
| AC-04 | Validation and uniqueness | API/constraint/concurrency | Exact errors; no duplicates |
| AC-05 | Deterministic list behavior | Repository/API parameterized | Exact defaults/ties |
| AC-06 | Hierarchy safety | Service/concurrency | No cycle/race/infinite loop |
| AC-07 | Delete boundary | Delete/FK tests | Child blocked; Brand succeeds |
| AC-08 | Permission rollout | Security integration | DB change effective next request/same token |
| AC-09 | Mapper/module boundary | Mapper/Spring/source scans | Exact fields/nulls/no boundary breach |
| AC-10 | General gates | Full suite/diff/credential | PASS; Docker prerequisite |

### Exact Verification Matrix

- Migration: real current-version→next with existing IAM rows; fresh schema; exact objects/timestamps; permission missing/compatible/conflict; UUID collision; missing/incompatible SHOP_OWNER; existing assignment; rollback/no partial mutation.
- Direct SQL: null/blank/whitespace/too-long/invalid codes; case-insensitive duplicate codes; blank names; duplicate root/sibling; same name under different parents; duplicate Brand; invalid status; self-parent; timestamp defaults/trigger/preserved created_at.
- Metadata: both Hibernate/PostgreSQL structured paths for each mapping; null/unknown => DB_001; prove no substring matching.
- Hierarchy: root/child, missing parent, self/multi-level/corrupt cycle, delete child conflict, reciprocal reparent, create/move child vs delete parent, advisory lock acquisition/rollback/release; Brand/read not serialized.
- API: CRUD, immutable code, normalization examples, description 5000/5001, pagination/search/status/sort/ties, invalid params, unknown JSON, stable errors, eight permission allow/deny cases.
- Authority: issue SHOP_OWNER token before assignment; assign; next same-token request succeeds; remove; next same-token request denied; no JWT refresh/reissue/session revoke.
- Mapper/general: mappings/null/page/Spring bean; JPA discovery; Bruno variables/no token hardcoding; `.\mvnw.cmd clean test` with Docker, `git diff --check`, credential scan without printing values.

## 8. Resolved Decisions

| Decision | Owner Resolution | Status |
|---|---|---|
| OD-01 | UUID; exact code/name/description limits | `RESOLVED` |
| OD-02 | `ACTIVE|INACTIVE`, default ACTIVE, DB CHECK | `RESOLVED` |
| OD-03 | Exact scoped name uniqueness and global stable codes | `RESOLVED` |
| OD-04 | Exact Category DB timestamp function/trigger; no Brand timestamps | `RESOLVED` |
| OD-05 | Self-FK RESTRICT; hard delete; Product dependency excluded | `RESOLVED` |
| OD-06 | Flat list; inactive parent may have active child; no cascade | `RESOLVED` |
| OD-07 | Fixed advisory lock and bounded traversal; no version | `RESOLVED` |
| OD-08 | Exact permissions assigned SHOP_OWNER; DB reload next request | `RESOLVED` |

No owner decision remains open. Work item stays `DESIGN_REVIEW/PENDING` until CODEX_REVIEW verdict; CODEX_PLAN does not approve or move it to READY.
