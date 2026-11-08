# Flyway database migrations

## Canonical baseline

The development migration history was squashed on 2026-10-27 because all existing data was explicitly confirmed to be disposable test data.

The repository now has one empty-database baseline:

| Version | Migration | Purpose |
|---|---|---|
| 1 | `V1__create_aquasuite_baseline.sql` | Creates the complete canonical PostgreSQL schema, indexes, constraints, triggers, IAM roles and permissions |

Java 21 / Spring Boot 3.5.16 manages Flyway. Hibernate must use `ddl-auto=validate`; it must not create or update the schema.

## Compatibility boundary

This baseline supports a new, empty PostgreSQL database. It is not compatible with the former V1-V16 Flyway history and must not be introduced into an existing shared, staging or production database.

Any future non-disposable database that already has migration history requires a separate, reviewed forward-migration or onboarding procedure. Do not automatically baseline, repair, delete Flyway history or recreate such a database.

## Adding schema changes

Do not edit V1 after it has been adopted outside disposable development environments. New schema changes continue with V2, V3 and later forward-only migrations.

Every schema change must:

- preserve PostgreSQL as the canonical database;
- keep Hibernate validation passing;
- include a migration-level or integration regression test;
- avoid secrets and environment-specific data;
- follow the module ownership and review requirements in `AGENTS.md`.

## Verification

The baseline is verified on a disposable PostgreSQL database by `FlywayMigrationIT`:

- a fresh database applies exactly one migration;
- a repeated migrate performs no work;
- Flyway validation succeeds;
- a non-empty unmanaged schema is rejected;
- category inserts that omit timestamps receive database-generated `created_at` and `updated_at` values.

Application integration tests additionally run with `spring.jpa.hibernate.ddl-auto=validate` to detect divergence between JPA mappings and the baseline.
