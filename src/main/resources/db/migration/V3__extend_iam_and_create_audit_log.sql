ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.user_roles
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS assigned_by UUID;

DO $$
BEGIN
    IF EXISTS (
        SELECT phone
        FROM public.users
        WHERE phone IS NOT NULL
        GROUP BY phone
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot create uq_users_phone: duplicate non-null phone values exist';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_phone ON public.users (phone) WHERE phone IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.audit_logs (
    audit_id UUID NOT NULL,
    user_id UUID,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    old_data JSONB,
    new_data JSONB,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_logs PRIMARY KEY (audit_id)
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON public.audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON public.audit_logs (user_id, created_at DESC);
