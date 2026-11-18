-- Preserve UC-45 warranty-record schema after adopting the UC-44 policy table from V33.
-- 2. Upgrade public.warranty_records
ALTER TABLE public.warranty_records
    ADD COLUMN IF NOT EXISTS warranty_code VARCHAR(32),
    ADD COLUMN IF NOT EXISTS warranty_policy_id UUID,
    ADD COLUMN IF NOT EXISTS order_id UUID,
    ADD COLUMN IF NOT EXISTS delivery_id UUID,
    ADD COLUMN IF NOT EXISTS warranty_months INT NOT NULL DEFAULT 12,
    ADD COLUMN IF NOT EXISTS void_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS voided_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS voided_by UUID,
    ADD COLUMN IF NOT EXISTS created_by UUID,
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE public.warranty_records
    ALTER COLUMN order_item_id DROP NOT NULL,
    ALTER COLUMN serial_number DROP NOT NULL;

-- Backfill warranty_code if legacy rows exist without code
WITH numbered AS (
    SELECT warranty_record_id,
           row_number() OVER (
               PARTITION BY to_char(start_date, 'YYYYMM')
               ORDER BY start_date, warranty_record_id
           ) AS rn
    FROM public.warranty_records
    WHERE warranty_code IS NULL
)
UPDATE public.warranty_records w
SET warranty_code = 'WAR-' || to_char(w.start_date, 'YYYYMM') || '-' || lpad(numbered.rn::text, 4, '0')
FROM numbered
WHERE w.warranty_record_id = numbered.warranty_record_id;

ALTER TABLE public.warranty_records
    ALTER COLUMN warranty_code SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'uq_warranty_records_code') THEN
        ALTER TABLE public.warranty_records
            ADD CONSTRAINT uq_warranty_records_code UNIQUE (warranty_code);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_warranty_records_policy') THEN
        ALTER TABLE public.warranty_records
            ADD CONSTRAINT fk_warranty_records_policy
                FOREIGN KEY (warranty_policy_id)
                REFERENCES public.warranty_policies (warranty_policy_id)
                ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_warranty_records_created_by') THEN
        ALTER TABLE public.warranty_records
            ADD CONSTRAINT fk_warranty_records_created_by
                FOREIGN KEY (created_by)
                REFERENCES public.users (id)
                ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'fk_warranty_records_voided_by') THEN
        ALTER TABLE public.warranty_records
            ADD CONSTRAINT fk_warranty_records_voided_by
                FOREIGN KEY (voided_by)
                REFERENCES public.users (id)
                ON DELETE RESTRICT;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_warranty_status') THEN
        ALTER TABLE public.warranty_records
            ADD CONSTRAINT ck_warranty_status CHECK (status IN ('ACTIVE', 'VOIDED'));
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_warranty_dates') THEN
        ALTER TABLE public.warranty_records
            ADD CONSTRAINT ck_warranty_dates CHECK (end_date >= start_date);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_warranty_months') THEN
        ALTER TABLE public.warranty_records
            ADD CONSTRAINT ck_warranty_months CHECK (warranty_months > 0);
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'ck_warranty_void_consistency') THEN
        ALTER TABLE public.warranty_records
            ADD CONSTRAINT ck_warranty_void_consistency CHECK (
                (status = 'VOIDED' AND void_reason IS NOT NULL AND btrim(void_reason) <> '' AND voided_at IS NOT NULL)
                OR
                (status = 'ACTIVE' AND void_reason IS NULL AND voided_at IS NULL AND voided_by IS NULL)
            );
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_warranty_active_serial_variant
    ON public.warranty_records (variant_id, upper(btrim(serial_number)))
    WHERE status = 'ACTIVE' AND serial_number IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_warranty_active_noserial_delivery
    ON public.warranty_records (order_item_id, delivery_id)
    WHERE status = 'ACTIVE' AND serial_number IS NULL AND delivery_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_warranty_records_order_id ON public.warranty_records (order_id);
CREATE INDEX IF NOT EXISTS idx_warranty_records_policy_id ON public.warranty_records (warranty_policy_id);
CREATE INDEX IF NOT EXISTS idx_warranty_records_end_date ON public.warranty_records (end_date);

-- 3. Monthly warranty code atomic counter
CREATE TABLE IF NOT EXISTS public.warranty_code_counters (
    year_month CHAR(6) NOT NULL,
    last_seq INT NOT NULL,
    CONSTRAINT pk_warranty_code_counters PRIMARY KEY (year_month),
    CONSTRAINT ck_warranty_code_counters_seq CHECK (last_seq >= 0)
);

