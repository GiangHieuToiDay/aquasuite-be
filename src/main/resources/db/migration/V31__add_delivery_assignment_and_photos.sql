-- Additive delivery metadata; preserve legacy statuses and do not invent evidence.
ALTER TABLE public.deliveries
    ADD COLUMN assigned_by UUID,
    ADD COLUMN assigned_at TIMESTAMPTZ,
    ADD COLUMN started_at TIMESTAMPTZ,
    ADD COLUMN proof_photos JSONB NOT NULL DEFAULT '[]'::jsonb,
    ADD CONSTRAINT chk_deliveries_proof_photos_array CHECK (jsonb_typeof(proof_photos) = 'array');
