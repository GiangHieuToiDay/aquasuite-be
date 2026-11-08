# Squash Flyway Baseline

## Status

Approved by repository owner on 2026-10-27 for the disposable development database.

## Goal

Replace the development-only V1-V16 migration history with one canonical migration that builds the complete current AquaSuite PostgreSQL schema from an empty database.

## Scope and constraints

- Preserve the effective schema, constraints, indexes, triggers, canonical IAM data and catalog permissions from the current chain.
- Support empty databases only; this baseline is not an upgrade path for databases that already contain real or shared data.
- Keep Hibernate schema management at `ddl-auto=validate`.
- Reset only the explicitly configured local development database after verifying the replacement baseline on disposable PostgreSQL.

## Acceptance criteria

- Exactly one versioned SQL migration remains.
- A fresh PostgreSQL database migrates successfully and a repeated migrate is a no-op.
- Hibernate validates the resulting schema.
- Category inserts that omit timestamps receive non-null database-generated timestamps.
- Migration tests no longer depend on historical intermediate versions.

## Rollout

Existing local Flyway history is incompatible and the disposable development database must be recreated. Any future non-disposable environment requires a separately reviewed onboarding strategy.
