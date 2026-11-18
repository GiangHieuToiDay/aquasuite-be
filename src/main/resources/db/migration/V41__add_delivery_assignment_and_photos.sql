-- Delivery metadata moved forward after the merged V31 UC-45 migration.
ALTER TABLE public.deliveries
    ADD COLUMN IF NOT EXISTS assigned_by UUID,
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS proof_photos JSONB NOT NULL DEFAULT '[]'::jsonb;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'chk_deliveries_proof_photos_array') THEN
        ALTER TABLE public.deliveries
            ADD CONSTRAINT chk_deliveries_proof_photos_array
            CHECK (jsonb_typeof(proof_photos) = 'array');
    END IF;
END $$;
