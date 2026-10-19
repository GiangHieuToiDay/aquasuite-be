[CmdletBinding()]
param(
    [string]$ContainerId,
    [string]$Database = $env:PGDATABASE,
    [string]$Username = $env:PGUSER,
    [string]$SqlDirectory
)
$ErrorActionPreference = 'Stop'
if ([string]::IsNullOrWhiteSpace($Database) -or [string]::IsNullOrWhiteSpace($Username)) {
    throw 'Database and Username (or PGDATABASE/PGUSER) are required.'
}
if (!$SqlDirectory) { $SqlDirectory = Join-Path $PSScriptRoot '../../src/main/resources/db/manual' }
$migration = Join-Path $SqlDirectory 'uc13-phase1-v001.sql'
$checksum = (Get-FileHash -LiteralPath $migration -Algorithm SHA256).Hash.ToLowerInvariant()
$preflight = Get-Content -LiteralPath (Join-Path $SqlDirectory 'uc13-phase1-preflight.sql') -Raw -Encoding UTF8
$ddl = Get-Content -LiteralPath $migration -Raw -Encoding UTF8
$verify = Get-Content -LiteralPath (Join-Path $SqlDirectory 'uc13-phase1-verify.sql') -Raw -Encoding UTF8
# The only interpolated SQL value is a SHA-256 hexadecimal digest, never credentials.
$sql = @"
\set ON_ERROR_STOP on
BEGIN;
SELECT pg_advisory_xact_lock(13013001);
CREATE TABLE IF NOT EXISTS catalog_bundle_schema_versions(version INTEGER PRIMARY KEY, checksum VARCHAR(64) NOT NULL, applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP);
SELECT EXISTS(SELECT 1 FROM catalog_bundle_schema_versions WHERE version=1) AS already_applied,
       EXISTS(SELECT 1 FROM catalog_bundle_schema_versions WHERE version=1 AND checksum<>'$checksum') AS mismatch \gset
\if :mismatch
DO `$`$ BEGIN RAISE EXCEPTION 'UC13 migration checksum mismatch'; END `$`$;
\endif
\if :already_applied
\echo UC13 migration already applied with matching checksum
\else
$preflight
$ddl
$verify
INSERT INTO catalog_bundle_schema_versions(version,checksum) VALUES(1,'$checksum');
\endif
COMMIT;
"@
$oldEncoding = $OutputEncoding
try {
    $OutputEncoding = New-Object System.Text.UTF8Encoding($false)
    if ($ContainerId) {
        if ($ContainerId -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]*$') { throw 'Invalid explicit container ID.' }
        $sql | & docker exec -i $ContainerId psql -X -v ON_ERROR_STOP=1 -U $Username -d $Database
    } else {
        $sql | & psql -X -v ON_ERROR_STOP=1 -U $Username -d $Database
    }
    if ($LASTEXITCODE -ne 0) { throw "UC13 migration failed (psql exit $LASTEXITCODE). Transaction was rolled back." }
} finally {
    $OutputEncoding = $oldEncoding
}
