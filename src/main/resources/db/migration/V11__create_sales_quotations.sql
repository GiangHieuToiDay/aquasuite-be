-- ====================================================================
-- V11: Module Sales - Quotations & Quotation Items
-- ====================================================================

-- 1. Table: quotations (29 columns)
CREATE TABLE IF NOT EXISTS public.quotations (
    quotation_id UUID NOT NULL,
    quotation_code VARCHAR(100) NOT NULL,
    revision_no INTEGER NOT NULL DEFAULT 1,
    parent_quotation_id UUID,
    customer_id UUID NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    delivery_address TEXT,
    delivery_schedule TEXT,
    note TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    quotation_date DATE NOT NULL,
    valid_until DATE NOT NULL,
    vat_rate NUMERIC(5,2) NOT NULL DEFAULT 10,
    shipping_fee NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    approval_note TEXT,
    margin_percent_at_approval NUMERIC(5,2),
    sent_at TIMESTAMPTZ,
    sent_to_email VARCHAR(320),
    pdf_url TEXT,
    accepted_at TIMESTAMPTZ,
    signed_file_url TEXT,
    lost_at TIMESTAMPTZ,
    lost_reason TEXT,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_quotations PRIMARY KEY (quotation_id),
    CONSTRAINT uq_quotations_code_revision UNIQUE (quotation_code, revision_no),
    CONSTRAINT chk_quotations_vat_rate CHECK (vat_rate IN (0, 8, 10)),
    CONSTRAINT chk_quotations_shipping_fee CHECK (shipping_fee >= 0),
    CONSTRAINT chk_quotations_valid_until CHECK (valid_until >= quotation_date)
);

CREATE INDEX IF NOT EXISTS idx_quotations_customer_id ON public.quotations (customer_id);
CREATE INDEX IF NOT EXISTS idx_quotations_status ON public.quotations (status);
CREATE INDEX IF NOT EXISTS idx_quotations_parent_id ON public.quotations (parent_quotation_id);
CREATE INDEX IF NOT EXISTS idx_quotations_date ON public.quotations (quotation_date);

-- 2. Table: quotation_items (13 columns)
-- Drop legacy placeholder table created in V5 to match exact 13-column schema and ON DELETE CASCADE
DROP TABLE IF EXISTS public.quotation_items CASCADE;

CREATE TABLE public.quotation_items (
    quotation_item_id UUID NOT NULL,
    quotation_id UUID NOT NULL,
    parent_item_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    sku VARCHAR(100) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    quantity NUMERIC(12,2) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL,
    discount_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    unit_cost NUMERIC(15,2) NOT NULL DEFAULT 0,
    backorder_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    lead_time_days INTEGER,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_quotation_items PRIMARY KEY (quotation_item_id),
    CONSTRAINT fk_quotation_items_quotation FOREIGN KEY (quotation_id) REFERENCES public.quotations (quotation_id) ON DELETE CASCADE,
    CONSTRAINT chk_quotation_items_quantity CHECK (quantity > 0),
    CONSTRAINT chk_quotation_items_discount_rate CHECK (discount_rate >= 0 AND discount_rate <= 100),
    CONSTRAINT chk_quotation_items_backorder_qty CHECK (backorder_qty >= 0)
);

CREATE INDEX IF NOT EXISTS idx_quotation_items_quotation_id ON public.quotation_items (quotation_id);
CREATE INDEX IF NOT EXISTS idx_quotation_items_parent_item_id ON public.quotation_items (parent_item_id);
CREATE INDEX IF NOT EXISTS idx_quotation_items_variant_id ON public.quotation_items (variant_id);
