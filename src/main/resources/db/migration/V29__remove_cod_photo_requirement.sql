-- Stop requiring photos without discarding legacy evidence or rewriting applied V28.
ALTER TABLE cod_collections
    ALTER COLUMN proof_type DROP NOT NULL,
    ALTER COLUMN proof_content DROP NOT NULL;
