-- Complete fields mapped by current entities but absent from the reconciled V1-V10 chain.
-- The superseded numeric-ID and duplicate module baselines must not be restored.

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS password_last_changed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS verification_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS fcm_token TEXT,
    ADD COLUMN IF NOT EXISTS verification_expired_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS email_sent_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS email_sent_date DATE;

ALTER TABLE public.brands
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

