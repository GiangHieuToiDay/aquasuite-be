-- Add batch scheduling and issuance/cancellation audit fields to sales_invoices.
-- Supports multiple PROJECT batch invoices (DRAFT -> ISSUED -> CANCELLED) per order.
ALTER TABLE public.sales_invoices
    ADD COLUMN IF NOT EXISTS batch_no INTEGER NOT NULL DEFAULT 1,
    ADD COLUMN IF NOT EXISTS batch_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS scheduled_delivery_date TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS issued_by UUID,
    ADD COLUMN IF NOT EXISTS issued_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS cancelled_by UUID,
    ADD COLUMN IF NOT EXISTS cancelled_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS note TEXT;

CREATE INDEX IF NOT EXISTS idx_sales_invoices_order_batch
    ON public.sales_invoices (order_id, batch_no);

