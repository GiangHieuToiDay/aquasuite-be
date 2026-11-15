-- UC-45: Warranty Policies & E-Warranty Records Enhancement
-- Method B: Create warranty_policies table first (linkable via product_variants.warranty_policy_id)
-- and upgrade public.warranty_records with warranty_code, policy snapshot, order/delivery links,
-- void audit metadata, DB constraints, partial unique indexes, and atomic monthly code counter.

-- 1. Create public.warranty_policies
CREATE TABLE IF NOT EXISTS public.warranty_policies (
    warranty_policy_id UUID NOT NULL,
    policy_code VARCHAR(50) NOT NULL,
    policy_name VARCHAR(150) NOT NULL,
    warranty_months INT NOT NULL,
    warranty_terms TEXT,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_warranty_policies PRIMARY KEY (warranty_policy_id),
    CONSTRAINT uq_warranty_policies_code UNIQUE (policy_code),
    CONSTRAINT ck_warranty_policies_months CHECK (warranty_months >= 0),
    CONSTRAINT ck_warranty_policies_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX IF NOT EXISTS idx_warranty_policies_status ON public.warranty_policies (status);

-- Clean up any unlinked placeholder policy IDs before adding FK (safe on empty/legacy dev DBs)
UPDATE public.product_variants pv
SET warranty_policy_id = NULL
WHERE pv.warranty_policy_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM public.warranty_policies wp
      WHERE wp.warranty_policy_id = pv.warranty_policy_id
  );

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_product_variants_warranty_policy'
    ) THEN
        ALTER TABLE public.product_variants
            ADD CONSTRAINT fk_product_variants_warranty_policy
                FOREIGN KEY (warranty_policy_id)
                REFERENCES public.warranty_policies (warranty_policy_id)
                ON DELETE RESTRICT;
    END IF;
END $$;

-- Seed baseline warranty policies (idempotent)
INSERT INTO public.warranty_policies (
    warranty_policy_id, policy_code, policy_name, warranty_months, warranty_terms, description, status
) VALUES
    ('11111111-1111-4111-8111-000000000000', 'WP-NONE', 'Không áp dụng bảo hành', 0,
     'Sản phẩm/vật tư tiêu hao không thuộc diện bảo hành.',
     'Dùng cho phụ kiện tiêu hao hoặc sản phẩm không kích hoạt bảo hành.', 'ACTIVE'),
    ('11111111-1111-4111-8111-000000000012', 'WP-12M', 'Bảo hành tiêu chuẩn 12 tháng', 12,
     'Bảo hành chính hãng 12 tháng đối với lỗi kỹ thuật từ nhà sản xuất; không áp dụng với hư hỏng do lắp đặt sai hoặc thiên tai.',
     'Chính sách bảo hành 12 tháng tiêu chuẩn.', 'ACTIVE'),
    ('11111111-1111-4111-8111-000000000024', 'WP-24M', 'Bảo hành thiết bị điện tử 24 tháng', 24,
     'Bảo hành 24 tháng cho linh kiện điện tử, van điện tử và bo mạch điều khiển chính hãng.',
     'Áp dụng cho nắp rửa điện tử, bồn cầu thông minh, máy bơm nước.', 'ACTIVE'),
    ('11111111-1111-4111-8111-000000000060', 'WP-60M', 'Bảo hành sen vòi & phụ kiện 60 tháng', 60,
     'Bảo hành 60 tháng cho thân van đồng thau, lớp mạ và lõi chia nước chính hãng.',
     'Áp dụng cho sen tắm, vòi chậu và phụ kiện cao cấp.', 'ACTIVE'),
    ('11111111-1111-4111-8111-000000000120', 'WP-120M', 'Bảo hành phần sứ 120 tháng', 120,
     'Bảo hành 10 năm (120 tháng) cho phần sứ vệ sinh chính hãng đối với lỗi men nứt vỡ tự nhiên.',
     'Áp dụng cho bồn cầu, chậu rửa sứ cao cấp.', 'ACTIVE')
ON CONFLICT (policy_code) DO NOTHING;

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

