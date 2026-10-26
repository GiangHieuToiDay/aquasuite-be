# FIX-FLYWAY-CHAIN — Reconcile the fresh-database Flyway chain

## Metadata

- **Work ID**: `FIX-FLYWAY-CHAIN`
- **Feature / Use Case**: Restore deterministic application schema migration
- **Version**: `1.0.0`
- **Owner**: Repository owner
- **Owning Module**: IAM / catalog / inventory / sales schema
- **Risk Reasons**: data migration, authentication schema, cross-module schema
- **Design Review**: `APPROVED`
- **Decision Owner**: Repository owner (approved 2026-10-26)

## 1. Goal and Scope

### Problem and Desired Outcome

Flyway cannot start because legacy module baselines duplicate versions 1, 2 and 3 and define schemas incompatible with the canonical UUID-based chain. A fresh PostgreSQL database must migrate deterministically through one migration per version and contain every column mapped by the current IAM `User` entity.

### In Scope

- Remove the three superseded baseline scripts from the active Flyway location.
- Add V11 for entity-mapped IAM/catalog columns absent from the canonical chain.
- Add V12 and align Sales quotation entities with the project's canonical UUID identifiers.
- Reconcile post-merge Sales/inventory schema through forward-only V13 and create remaining entity tables through V14.
- Update the Flyway runbook and verify source/build/application migration behavior.

### Out of Scope

- Flyway repair, baseline, database recreation or data backfill.
- Reworking unrelated historical specifications and integration fixtures.

## 2. Actors and Security

Not applicable to runtime actors. Authentication data remains in the IAM-owned `users` table. No credential values are introduced or logged.

## 3. Business Rules

1. `users.id` remains UUID and `password_hash` remains the credential column.
2. The canonical IAM role/permission join model remains authoritative.
3. Each Flyway version is unique.
4. Existing environments require separate history/schema inventory before onboarding.
5. Sales scalar identifiers use UUID consistently across module boundaries.

## 4. Data and Migration Decisions

- Remove legacy `V1__init_iam.sql`, `V2__init_catalog.sql` and `V3__init_inventory.sql` from `classpath:db/migration` because their tables/types conflict with the canonical chain.
- Add nullable/defaulted IAM columns and defaulted Brand timestamps only in V11; no existing row is deleted or rewritten.
- V11 uses `ADD COLUMN IF NOT EXISTS` so a compatible pre-existing column is retained.
- V12 creates the missing quotation aggregate tables and expands the empty V5 quotation-item placeholder.
- The local V5 quotation-item table was verified to contain zero rows before V12 was applied.
- The merged duplicate `V11__create_sales_quotations.sql` is removed rather than renumbered because it drops a V5/V12-owned table and conflicts with applied history.
- V13/V14 are forward-only; applied V11/V12 files and history remain unchanged.
- No cross-module JPA relationships are introduced.

## 5. Contracts

No HTTP, Java API or domain-event contract changes.

## 6. Concurrency, Transaction and Failure Safety

- Flyway supplies the migration transaction boundary.
- The migration is additive and retry-safe for compatible existing columns.
- Incompatible existing databases must stop for reviewed reconciliation; no automatic repair is allowed.

## 7. Acceptance Criteria and Verification

| AC | Observable Outcome | Verification | Expected Result |
|---|---|---|---|
| AC-01 | All Flyway versions are unique | Filename/version scan | No duplicate versions |
| AC-02 | Legacy incompatible baselines are inactive | Source inspection | Three obsolete files absent |
| AC-03 | Current User and Brand fields have schema columns | Migration/entity comparison | Missing fields supplied by V11 |
| AC-04 | Production source compiles | Maven compile | Build success |
| AC-05 | Application-context migration can proceed | Relevant integration/startup test | No duplicate-version Flyway error |
| AC-06 | Hibernate accepts the Sales quotation schema | Start the application with `ddl-auto=validate` | EntityManagerFactory initializes |
| AC-07 | Post-merge entities have owned tables/columns | Full application startup at V14 | Hibernate validation and application startup succeed |

## 8. Open Decisions

| Decision | Owner | Required Before | Status/Resolution |
|---|---|---|---|
| Existing database onboarding | Environment owner | Deployment to a non-empty database | Inspect backup, history and actual schema first |
