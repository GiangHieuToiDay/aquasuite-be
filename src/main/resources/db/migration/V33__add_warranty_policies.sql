-- UC-44: live warranty-policy definitions. Warranty records retain frozen terms at issuance.
CREATE SEQUENCE IF NOT EXISTS public.warranty_policy_code_seq START WITH 1 INCREMENT BY 1;

CREATE TABLE IF NOT EXISTS public.warranty_policies (
    warranty_policy_id UUID NOT NULL,
    policy_code VARCHAR(32) NOT NULL,
    policy_name VARCHAR(255) NOT NULL,
    brand_id UUID NOT NULL,
    category_id UUID NOT NULL,
    warranty_period_months INTEGER NOT NULL,
    coverage_conditions TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_warranty_policies PRIMARY KEY (warranty_policy_id),
    CONSTRAINT uq_warranty_policies_code UNIQUE (policy_code),
    CONSTRAINT fk_warranty_policies_brand FOREIGN KEY (brand_id) REFERENCES public.brands (brand_id) ON DELETE RESTRICT,
    CONSTRAINT fk_warranty_policies_category FOREIGN KEY (category_id) REFERENCES public.categories (category_id) ON DELETE RESTRICT,
    CONSTRAINT fk_warranty_policies_created_by FOREIGN KEY (created_by) REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_warranty_policies_updated_by FOREIGN KEY (updated_by) REFERENCES public.users (id) ON DELETE SET NULL,
    CONSTRAINT chk_warranty_policies_period_positive CHECK (warranty_period_months > 0),
    CONSTRAINT chk_warranty_policies_name_not_blank CHECK (btrim(policy_name) <> ''),
    CONSTRAINT chk_warranty_policies_coverage_not_blank CHECK (btrim(coverage_conditions) <> ''),
    CONSTRAINT chk_warranty_policies_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_warranty_policies_active_brand_category
    ON public.warranty_policies (brand_id, category_id) WHERE status = 'ACTIVE';
CREATE INDEX IF NOT EXISTS idx_warranty_policies_status ON public.warranty_policies (status);
CREATE INDEX IF NOT EXISTS idx_warranty_policies_brand_category ON public.warranty_policies (brand_id, category_id);

-- ProductVariant keeps the scalar ID in Java; the database FK protects integrity.
ALTER TABLE public.product_variants
    ADD CONSTRAINT fk_product_variants_warranty_policy
    FOREIGN KEY (warranty_policy_id) REFERENCES public.warranty_policies (warranty_policy_id) ON DELETE RESTRICT NOT VALID;
CREATE INDEX IF NOT EXISTS idx_product_variants_warranty_policy_id
    ON public.product_variants (warranty_policy_id) WHERE warranty_policy_id IS NOT NULL;
