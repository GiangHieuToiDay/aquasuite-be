-- Existing STORE duplicates, if any, must be resolved before this index is created.
CREATE UNIQUE INDEX uq_sales_invoices_store_order
    ON public.sales_invoices (order_id)
    WHERE invoice_type = 'STORE';
