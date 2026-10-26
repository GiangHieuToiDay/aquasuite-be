-- Forward-only reconciliation for Sales entities merged after V12.
-- V11 and V12 are immutable because they are already recorded in deployed history.

-- Quotations: preserve the V12 table and adapt renamed/current entity fields.
ALTER TABLE public.quotations RENAME COLUMN revision_number TO revision_no;
ALTER TABLE public.quotations RENAME COLUMN shipping_address TO delivery_address;
ALTER TABLE public.quotations RENAME COLUMN notes TO note;
ALTER TABLE public.quotations RENAME COLUMN tax_rate TO vat_rate;
ALTER TABLE public.quotations RENAME COLUMN pdf_file_url TO pdf_url;

ALTER TABLE public.quotations
    ALTER COLUMN quotation_code TYPE VARCHAR(100),
    ALTER COLUMN status TYPE VARCHAR(32),
    ALTER COLUMN shipping_fee TYPE NUMERIC(15,2),
    ALTER COLUMN total_amount TYPE NUMERIC(15,2),
    ALTER COLUMN sent_to_email TYPE VARCHAR(320),
    ALTER COLUMN signed_file_url TYPE TEXT,
    ALTER COLUMN lost_reason TYPE TEXT,
    ADD COLUMN approved_by UUID,
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN approval_note TEXT,
    ADD COLUMN margin_percent_at_approval NUMERIC(5,2),
    ADD COLUMN lost_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_quotations_code_revision
    ON public.quotations (quotation_code, revision_no);
CREATE INDEX IF NOT EXISTS idx_quotations_date
    ON public.quotations (quotation_date);

-- Quotation items: reuse V5/V12 rows and map the current Sales snapshot names.
ALTER TABLE public.quotation_items RENAME COLUMN sku_snapshot TO sku;
ALTER TABLE public.quotation_items RENAME COLUMN cost_price_snapshot TO unit_cost;
ALTER TABLE public.quotation_items RENAME COLUMN discount_percent TO discount_rate;

ALTER TABLE public.quotation_items
    ALTER COLUMN sku TYPE VARCHAR(100),
    ALTER COLUMN unit_cost TYPE NUMERIC(15,2),
    ALTER COLUMN backorder_qty TYPE NUMERIC(12,2),
    ALTER COLUMN variant_id SET NOT NULL,
    ADD COLUMN unit VARCHAR(50);

-- Existing environments must explicitly reconcile incomplete quotation rows.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.quotation_items
        WHERE sku IS NULL OR unit IS NULL
    ) THEN
        RAISE EXCEPTION 'V13 preflight failed: quotation_items requires reviewed sku/unit backfill';
    END IF;
END $$;

ALTER TABLE public.quotation_items
    ALTER COLUMN sku SET NOT NULL,
    ALTER COLUMN unit SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_quotation_items_parent_item_id
    ON public.quotation_items (parent_item_id);

-- The merged cart entity is customer-scoped; retain the older carts table as
-- historical infrastructure while extending its empty item table safely.
ALTER TABLE public.cart_items
    ADD COLUMN customer_id UUID,
    ADD COLUMN updated_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.cart_items
        WHERE customer_id IS NULL OR updated_at IS NULL
    ) THEN
        RAISE EXCEPTION 'V13 preflight failed: cart_items requires reviewed customer/timestamp backfill';
    END IF;
END $$;

ALTER TABLE public.cart_items
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

CREATE UNIQUE INDEX uq_cart_items_customer_variant
    ON public.cart_items (customer_id, variant_id);
CREATE INDEX idx_cart_items_customer_id
    ON public.cart_items (customer_id);

-- Orders and invoices are aggregate roots newly introduced by the merge.
CREATE TABLE public.orders (
    order_id UUID NOT NULL,
    order_code VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    project_id UUID,
    quotation_id UUID,
    sales_staff_id UUID,
    promotion_id UUID,
    order_channel VARCHAR(32) NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    order_date TIMESTAMPTZ NOT NULL,
    due_date DATE,
    subtotal NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    delivery_address TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    CONSTRAINT uq_orders_order_code UNIQUE (order_code),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
        REFERENCES public.customers (customer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_quotation FOREIGN KEY (quotation_id)
        REFERENCES public.quotations (quotation_id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_sales_staff FOREIGN KEY (sales_staff_id)
        REFERENCES public.users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_orders_customer_id ON public.orders (customer_id);
CREATE INDEX idx_orders_project_id ON public.orders (project_id);
CREATE INDEX idx_orders_quotation_id ON public.orders (quotation_id);
CREATE INDEX idx_orders_sales_staff_id ON public.orders (sales_staff_id);
CREATE INDEX idx_orders_order_status ON public.orders (order_status);
CREATE INDEX idx_orders_payment_status ON public.orders (payment_status);
CREATE INDEX idx_orders_order_date ON public.orders (order_date);

ALTER TABLE public.order_items
    ALTER COLUMN order_id SET NOT NULL,
    ADD CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES public.orders (order_id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_order_items_order_id
    ON public.order_items (order_id);
ALTER INDEX public.idx_order_items_variant RENAME TO idx_order_items_variant_id;

CREATE TABLE public.sales_invoices (
    invoice_id UUID NOT NULL,
    invoice_code VARCHAR(100) NOT NULL,
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    invoice_type VARCHAR(32) NOT NULL,
    issue_date TIMESTAMPTZ NOT NULL,
    subtotal NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sales_invoices PRIMARY KEY (invoice_id),
    CONSTRAINT uq_sales_invoices_invoice_code UNIQUE (invoice_code),
    CONSTRAINT fk_sales_invoices_order FOREIGN KEY (order_id)
        REFERENCES public.orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_invoices_customer FOREIGN KEY (customer_id)
        REFERENCES public.customers (customer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_invoices_created_by FOREIGN KEY (created_by)
        REFERENCES public.users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_sales_invoices_order_id ON public.sales_invoices (order_id);
CREATE INDEX idx_sales_invoices_customer_id ON public.sales_invoices (customer_id);
CREATE INDEX idx_sales_invoices_status ON public.sales_invoices (status);
CREATE INDEX idx_sales_invoices_issue_date ON public.sales_invoices (issue_date);

ALTER TABLE public.sales_invoice_items
    ALTER COLUMN invoice_id SET NOT NULL,
    ALTER COLUMN order_item_id SET NOT NULL,
    ADD CONSTRAINT fk_sales_invoice_items_invoice FOREIGN KEY (invoice_id)
        REFERENCES public.sales_invoices (invoice_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_sales_invoice_items_order_item FOREIGN KEY (order_item_id)
        REFERENCES public.order_items (order_item_id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_sales_invoice_items_invoice_id
    ON public.sales_invoice_items (invoice_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_items_order_item_id
    ON public.sales_invoice_items (order_item_id);
ALTER INDEX public.idx_sales_invoice_items_variant RENAME TO idx_sales_invoice_items_variant_id;

-- Purchasing/receiving entities were merged with the Sales work and also need
-- a forward schema from the V5 goods-receipt placeholder.
CREATE TABLE public.purchase_orders (
    purchase_order_id UUID NOT NULL,
    po_code VARCHAR(100) NOT NULL,
    supplier_id UUID NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    order_date DATE NOT NULL,
    expected_date DATE,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    note TEXT,
    created_by UUID NOT NULL,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancelled_at TIMESTAMPTZ,
    cancel_reason TEXT,
    closed_by UUID,
    closed_at TIMESTAMPTZ,
    close_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_purchase_orders PRIMARY KEY (purchase_order_id),
    CONSTRAINT uq_purchase_orders_code UNIQUE (po_code),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id)
        REFERENCES public.suppliers (supplier_id) ON DELETE RESTRICT
);

CREATE INDEX idx_po_supplier_id ON public.purchase_orders (supplier_id);
CREATE INDEX idx_po_status ON public.purchase_orders (status);
CREATE INDEX idx_po_order_date ON public.purchase_orders (order_date);

CREATE TABLE public.purchase_order_items (
    purchase_order_item_id UUID NOT NULL,
    purchase_order_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    unit VARCHAR(50) NOT NULL,
    quantity_ordered NUMERIC(12,2) NOT NULL,
    quantity_received NUMERIC(12,2) NOT NULL DEFAULT 0,
    unit_cost NUMERIC(15,2) NOT NULL,
    line_total NUMERIC(15,2) NOT NULL,
    CONSTRAINT pk_purchase_order_items PRIMARY KEY (purchase_order_item_id),
    CONSTRAINT uq_po_items_po_variant UNIQUE (purchase_order_id, variant_id),
    CONSTRAINT fk_po_items_order FOREIGN KEY (purchase_order_id)
        REFERENCES public.purchase_orders (purchase_order_id) ON DELETE CASCADE,
    CONSTRAINT fk_po_items_variant FOREIGN KEY (variant_id)
        REFERENCES public.product_variants (variant_id) ON DELETE RESTRICT
);

CREATE INDEX idx_po_items_po_id ON public.purchase_order_items (purchase_order_id);
CREATE INDEX idx_po_items_variant_id ON public.purchase_order_items (variant_id);

CREATE TABLE public.goods_receipts (
    goods_receipt_id UUID NOT NULL,
    receipt_code VARCHAR(100) NOT NULL,
    purchase_order_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    receipt_date DATE NOT NULL,
    supplier_invoice_no VARCHAR(100),
    invoice_image_url TEXT,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    note TEXT,
    created_by UUID NOT NULL,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancelled_at TIMESTAMPTZ,
    cancel_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_goods_receipts PRIMARY KEY (goods_receipt_id),
    CONSTRAINT uq_goods_receipts_code UNIQUE (receipt_code),
    CONSTRAINT fk_goods_receipts_order FOREIGN KEY (purchase_order_id)
        REFERENCES public.purchase_orders (purchase_order_id) ON DELETE RESTRICT
);

CREATE INDEX idx_gr_po_id ON public.goods_receipts (purchase_order_id);
CREATE INDEX idx_gr_status ON public.goods_receipts (status);
CREATE INDEX idx_gr_receipt_date ON public.goods_receipts (receipt_date);

ALTER TABLE public.goods_receipt_items
    ADD COLUMN purchase_order_item_id UUID,
    ADD COLUMN unit VARCHAR(50);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.goods_receipt_items
        WHERE goods_receipt_id IS NULL OR purchase_order_item_id IS NULL OR unit IS NULL
    ) THEN
        RAISE EXCEPTION 'V13 preflight failed: goods_receipt_items requires reviewed receiving backfill';
    END IF;
END $$;

ALTER TABLE public.goods_receipt_items
    ALTER COLUMN goods_receipt_id SET NOT NULL,
    ALTER COLUMN purchase_order_item_id SET NOT NULL,
    ALTER COLUMN unit SET NOT NULL,
    ADD CONSTRAINT fk_gr_items_receipt FOREIGN KEY (goods_receipt_id)
        REFERENCES public.goods_receipts (goods_receipt_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_gr_items_po_item FOREIGN KEY (purchase_order_item_id)
        REFERENCES public.purchase_order_items (purchase_order_item_id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_goods_receipt_items_receipt_po_item
    ON public.goods_receipt_items (goods_receipt_id, purchase_order_item_id);
CREATE INDEX idx_goods_receipt_items_receipt_id
    ON public.goods_receipt_items (goods_receipt_id);
CREATE INDEX idx_goods_receipt_items_po_item_id
    ON public.goods_receipt_items (purchase_order_item_id);
