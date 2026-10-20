# Flyway database migration

Spring Boot 3.5.16 manages Flyway 11.7.2 (`flyway-core` and `flyway-database-postgresql`). Startup runs pending migrations before Hibernate `validate`. Connection uses the existing `SPRING_DATASOURCE_*` environment variables.

## New empty database

Customer profile revision: `V7__SCRUM08_customer_profiles.sql` adds CRM customers from Database.pdf, linked by unique UUID user_id with ON DELETE RESTRICT. It does not backfill profiles for existing accounts. New online profiles are created together with OTP activation, default RETAIL/ACTIVE, with nullable address/company. V1-V6 remain unchanged.

SCRUM08 adds `V6__SCRUM08_user_registration.sql` after V5: nullable `users.full_name` and a unique phone index for non-null phones. Preflight blocks noncanonical or duplicate legacy phone values without changing or deleting them. Reconcile on a backed-up/restored copy before applying V6; do not edit V1-V5, automatically repair history or run a destructive down migration. No new registration/CRM/session tables are introduced. Registration OTP uses Redis TTL instead of a cleanup job.

Create the PostgreSQL database itself, supply connection variables, then start the application. Flyway runs:

- `V1__Initial_legacy_schema.sql`: all current base tables (catalog, IAM and inventory); no business seed data.
- `V2__SCRUM14_phase1_combo_core.sql`: preflight, combo constraints/configuration/backfill and compatibility audit table. This does not integrate combo CRUD with Auth/Audit.

Flyway records versions/checksums in `flyway_schema_history`. Keep `ddl-auto=validate`; do not use Hibernate `update`, `create` or `create-drop` alongside Flyway.

## Existing database without Flyway history

Startup deliberately rejects a nonempty database with no history. Back up the database and compare its complete schema with the migrations before opting into a baseline. A baseline records an existing state; it does **not** create or repair missing tables, columns, constraints or indexes.

| Existing state after review | One-time baseline version | Next action |
|---|---|---|
| Complete V1 base schema, combo migration not applied | `1` | Startup applies V2/V3, backfills legacy bundles and adds option configuration |
| Complete V1 + V2 schema, including old manual UC13 migration | `2` | Startup applies V3 and retains existing combo prices, versions and audit data |
| Partial, unknown or Hibernate-generated schema missing migration constraints | None yet | Reconcile explicitly on a restored copy before adopting Flyway |

In the application launch environment, temporarily set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` and `SPRING_FLYWAY_BASELINE_VERSION=1` or `2` according to the reviewed state. Start once and verify `flyway_schema_history` and Hibernate validation. Then remove both variables. Never leave automatic baseline enabled for routine deployment.

Flyway is the only migration mechanism. The PowerShell runner, separate SQL resources and custom schema-version journal are no longer used. Databases upgraded before Flyway adoption still require baseline version 2 after complete schema review. Existing legacy journal tables may remain as historical metadata; the application does not read or modify them.

## Future changes

V3 is `V3__SCRUM14_phase2_combo_options.sql` (additive `bundle_component_groups` table with UUID whitelist arrays). Existing drafts need no backfill; publication dates remain unchanged. Add V4, then V5, etc. under `src/main/resources/db/migration`. Never edit migrations already applied to shared databases. PostgreSQL migrations run transactionally; SQL errors roll back the failed migration. Flyway validation detects modified applied migrations. `clean` is disabled. Use a new corrective migration and application rollback as appropriate; do not delete history or automatically run `repair`.

## Verification

`./mvnw.cmd clean -Pphase1-it verify` requires Docker. Integration tests use disposable PostgreSQL 17.6 databases, apply Flyway migrations, and validate all current entity mappings. Dedicated tests cover first startup/retry, refusal of unmanaged databases, explicit baseline/backfill, retention of existing combo data and disabled clean. No migration is run against the developer's local or production database by these tests.

References: [Spring Boot 3.5 database initialization](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html), [Flyway baseline](https://documentation.red-gate.com/flyway/reference/commands/baseline).

Latest Flyway-only verification: clean phase1-it verify BUILD SUCCESS at 2026-10-20 13:36:48 +07; 47 Surefire + 41 Failsafe = 88 PASS, zero failures/errors/skipped. Evidence: flyway-only-verify.log (ignored local log). Combo migration tests also verify checksum mismatch, invalid-data rollback and database constraints.


Phase 2 verification is tracked separately in the Phase 2 handoff; the Flyway-only 88-test result above predates V3. Historical V2 migration tests remain pinned to target 2; full application/Flyway tests migrate through V3 and validate entity mappings. No migration has been applied to a live database by this task.

Phase 2 clean verification: BUILD SUCCESS, 111 PASS (56 + 55), zero failures/errors/skipped, 2026-10-20 15:05:17 +07; log `uc13-phase2-clean-verify.log`. V3 is tested on disposable PostgreSQL only.


## Authentication merge migration chain

The current chain is V1 legacy schema -> V2 combo core -> V3 options -> V4 UUID authentication -> V5 password recovery. Existing V1/V2/V3 bytes are preserved. The incoming IAM V1 DDL is integrated into V4 instead of introducing a second V1. V4 keeps original account rows in iam_legacy_users and maps numeric IDs deterministically in iam_legacy_user_ids, including historical orphan actor references; audit/stock-count/stock-movement scalar actor columns become UUID. Compatible password hashes/profile values are copied to the new users schema. Missing email/password identities are locked with non-deliverable placeholders; original values remain archived. Conflicting normalized usernames/emails fail the migration atomically for manual reconciliation. Old role IDs are retained in the archive; no role grants are guessed. V5 retains OTP hashes/rate-limit/password-change state separately.

Review/back up existing data before deployment. Databases already using this repository's V1/V2/V3 can upgrade forward. A database initialized from the standalone authentication branch has a different V1 checksum/schema: it must be reconciled and rehearsed on a restored copy, not blindly baseline/repair or run this chain. This task never migrates a local/shared/production database; tests use disposable containers.

Authentication integration verification: full verify 251 PASS, zero failures/errors/skipped (2026-10-20 17:56:28 +07). Three populated upgrade tests check account/profile/hash/recovery/actor/combo preservation, atomic rollback on conflicting identities, and locking incomplete identities. Legacy created_at is interpreted as UTC; raw values remain in the archive for reconciliation. No live migration run.
