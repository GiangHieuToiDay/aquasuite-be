-- UC-24: customer credit profile and invoice data used by internal receivables.
-- V1-V3 remain immutable. Existing customers intentionally receive no credit.

ALTER TABLE public.customers
    ADD COLUMN IF NOT EXISTS projects TEXT[],
    ADD COLUMN IF NOT EXISTS credit_limit NUMERIC(15, 2) NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS credit_term_days INTEGER;

-- Database.docx defines customers.address as a list. Preserve every existing scalar
-- value as a one-element array while retaining customer_addresses as the normalized
-- delivery-address source.
ALTER TABLE public.customers
    ALTER COLUMN address TYPE TEXT[]
        USING CASE WHEN address IS NULL THEN NULL ELSE ARRAY[address] END;

ALTER TABLE public.customers
    DROP CONSTRAINT IF EXISTS chk_customers_credit_profile,
    ADD CONSTRAINT chk_customers_credit_profile CHECK (
        (credit_limit = 0 AND credit_term_days IS NULL)
        OR (credit_limit > 0 AND credit_term_days BETWEEN 1 AND 30)
    );

CREATE INDEX IF NOT EXISTS idx_customers_credit_profile
    ON public.customers (customer_type, status)
    WHERE credit_limit > 0;

ALTER TABLE public.sales_invoices
    ADD COLUMN IF NOT EXISTS invoice_symbol VARCHAR(20),
    ADD COLUMN IF NOT EXISTS signed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS provider_code VARCHAR(50),
    ADD COLUMN IF NOT EXISTS provider_invoice_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS original_invoice_id UUID;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'fk_sales_invoices_original_invoice'
    ) THEN
        ALTER TABLE public.sales_invoices
            ADD CONSTRAINT fk_sales_invoices_original_invoice
                FOREIGN KEY (original_invoice_id)
                REFERENCES public.sales_invoices (invoice_id)
                ON DELETE RESTRICT;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_sales_invoices_original_invoice_id
    ON public.sales_invoices (original_invoice_id);
