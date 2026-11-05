-- UC-24: an invoice keeps the payment term effective when it becomes ISSUED.
-- This is additive and preserves the prior derived due-date behavior for existing rows.
ALTER TABLE public.sales_invoices
    ADD COLUMN IF NOT EXISTS credit_term_days INTEGER,
    ADD COLUMN IF NOT EXISTS due_date DATE;

UPDATE public.sales_invoices invoice
SET credit_term_days = COALESCE(customer.credit_term_days, 0),
    due_date = invoice.issue_date::date + COALESCE(customer.credit_term_days, 0)
FROM public.customers customer
WHERE invoice.customer_id = customer.customer_id
  AND invoice.due_date IS NULL;

ALTER TABLE public.sales_invoices
    DROP CONSTRAINT IF EXISTS chk_sales_invoices_credit_term_days,
    ADD CONSTRAINT chk_sales_invoices_credit_term_days
        CHECK (credit_term_days IS NULL OR credit_term_days BETWEEN 0 AND 30);

CREATE INDEX IF NOT EXISTS idx_sales_invoices_due_date
    ON public.sales_invoices (due_date)
    WHERE due_date IS NOT NULL;
