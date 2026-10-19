# UC13 phase 1 — core schema migration

Baseline: Java 21/Spring Boot 3.5.16. This is an additive PostgreSQL migration, separate from FEATURE-004. It does not create storefront/media/inventory/Sales tables or publish existing bundles.

Documentation revision 0.9.1 retains the existing SQL/checksum. Phase 1 core does not integrate Auth/Audit; the audit table retained by the earlier migration is compatibility schema, not a runtime prerequisite for combo CRUD. The subsequent plan 0.9.2 code verification passed 35 unit + 37 integration tests, including 3 migration tests against isolated PostgreSQL 17.6 databases. See the [current implementation handoff](../../.agent/handoffs/SCRUM14-UC13-phase1-verification.md) for commands and evidence. No SQL/runner change or live database migration was performed in that verification task.

| Step | Action |
|---|---|
| Before applying | Back up the target test database; inspect catalog schema/constraints. Runner preflight rejects invalid quantities, duplicates, orphan/nested components and variants owned by BUNDLE products. Correct data only after owner review |
| Connection | Supply PGHOST/PGPORT/PGDATABASE/PGUSER and credentials through the deployment environment or secret tooling. Do not place secrets in commands, this document or artifacts; PowerShell does not automatically load .env |
| Test/local psql | Run `./scripts/db/Apply-Uc13Phase1.ps1`; explicit Database/Username parameters are optional when PGDATABASE/PGUSER exist |
| Docker test | Run the script with `-ContainerId` explicitly identifying the isolated PostgreSQL container, and its test Database/Username. SQL is piped to psql inside that container; no host psql required |
| Concurrency/retry | Runner holds an advisory transaction lock, checks raw-byte SHA256 against journal version 1. Equal checksum skips; mismatch fails. No file/environment secret is embedded into SQL |
| Atomicity | Preflight, DDL, backfill, verification and journal commit together. SQL error terminates psql and rolls back. Do not apply v001 directly or add nested COMMIT statements |
| Backfill | Every existing BUNDLE gets DRAFT/version 0 with null price/dates. Existing ACTIVE/INACTIVE/DELETED Product status, code, item IDs and quantities remain unchanged |
| Verify | Review runner exit code and execute uc13-phase1-verify.sql. Check counts and constraints, then exercise Owner create/edit/status/delete on the test environment |
| App configuration | Keep common/dev/test/prod profiles. Core revision 0.9.0 does not require IAM/JWT configuration. Use SPRING_JPA_HIBERNATE_DDL_AUTO=validate after managed schema deployment |
| Rollback | Roll back application deployment while retaining additive schema/tombstones/audit. Do not DROP tables or rewrite history to recover from application errors. Test old application against upgraded schema before production rollout |
| Production | This task does not authorize running DDL on live databases. A human operator approves target and backup before any production migration |

The runner assumes base catalog tables exist. Existing unmanaged tables named bundle_configurations/audit_entries must be reconciled before running; it fails rather than silently adopting unknown schema. Migration does not automatically repair invalid legacy data.
