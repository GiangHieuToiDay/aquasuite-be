-- Complete the quotation schema introduced as a minimal variant-reference table in V5.
-- AquaSuite uses UUID identifiers across IAM, CRM, catalog and sales boundaries.

CREATE TABLE public.quotations (
    quotation_id UUID NOT NULL,
    quotation_code VARCHAR(50) NOT NULL,
    revision_number INTEGER NOT NULL DEFAULT 1,
    parent_quotation_id UUID,
    customer_id UUID NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    shipping_address TEXT,
    notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    quotation_date DATE NOT NULL,
    valid_until DATE NOT NULL,
    subtotal NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 10,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    shipping_fee NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    is_over_discount_limit BOOLEAN NOT NULL DEFAULT FALSE,
    estimated_margin_pct NUMERIC(5,2),
    delivery_schedule TEXT,
    pdf_file_url VARCHAR(500),
    sent_at TIMESTAMPTZ,
    sent_to_email VARCHAR(150),
    accepted_at TIMESTAMPTZ,
    signed_file_url VARCHAR(500),
    lost_reason VARCHAR(255),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_quotations PRIMARY KEY (quotation_id),
    CONSTRAINT uq_quotations_code UNIQUE (quotation_code),
    CONSTRAINT fk_quotations_parent FOREIGN KEY (parent_quotation_id)
        REFERENCES public.quotations (quotation_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quotations_customer FOREIGN KEY (customer_id)
        REFERENCES public.customers (customer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quotations_created_by FOREIGN KEY (created_by)
        REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quotations_status CHECK (status IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SENT',
        'ACCEPTED', 'LOST', 'EXPIRED', 'SUPERSEDED'
    ))
);

CREATE INDEX idx_quotations_customer_id ON public.quotations (customer_id);
CREATE INDEX idx_quotations_status ON public.quotations (status);
CREATE INDEX idx_quotations_parent_id ON public.quotations (parent_quotation_id);
CREATE INDEX idx_quotations_created_by ON public.quotations (created_by);

ALTER TABLE public.quotation_items
    ALTER COLUMN quotation_id SET NOT NULL,
    ALTER COLUMN variant_id DROP NOT NULL,
    ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN item_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN parent_item_id UUID,
    ADD COLUMN combo_id UUID,
    ADD COLUMN sku_snapshot VARCHAR(50),
    ADD COLUMN item_name_snapshot VARCHAR(255) NOT NULL,
    ADD COLUMN cost_price_snapshot NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN discount_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN net_unit_price NUMERIC(18,2) NOT NULL,
    ADD COLUMN available_stock_qty NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN incoming_stock_qty NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN backorder_qty NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN lead_time_days INTEGER,
    ADD CONSTRAINT fk_quotation_items_quotation FOREIGN KEY (quotation_id)
        REFERENCES public.quotations (quotation_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_quotation_items_parent FOREIGN KEY (parent_item_id)
        REFERENCES public.quotation_items (quotation_item_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_quotation_items_combo FOREIGN KEY (combo_id)
        REFERENCES public.products (product_id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_quotation_items_type CHECK (item_type IN (
        'SINGLE', 'COMBO_HEADER', 'COMBO_COMPONENT'
    ));

CREATE INDEX idx_quotation_items_quotation_id ON public.quotation_items (quotation_id);
CREATE INDEX idx_quotation_items_parent_id ON public.quotation_items (parent_item_id);
CREATE INDEX idx_quotation_items_combo_id ON public.quotation_items (combo_id);
ALTER INDEX public.idx_quotation_items_variant RENAME TO idx_quotation_items_variant_id;

CREATE TABLE public.quotation_payment_milestones (
    milestone_id UUID NOT NULL,
    quotation_id UUID NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    percent NUMERIC(5,2) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_quotation_payment_milestones PRIMARY KEY (milestone_id),
    CONSTRAINT fk_quotation_milestones_quotation FOREIGN KEY (quotation_id)
        REFERENCES public.quotations (quotation_id) ON DELETE CASCADE
);

CREATE INDEX idx_quotation_milestones_quotation_id
    ON public.quotation_payment_milestones (quotation_id);
