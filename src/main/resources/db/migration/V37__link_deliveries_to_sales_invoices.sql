-- Legacy rows remain nullable; never invent an invoice for an order without one.
ALTER TABLE public.deliveries ADD COLUMN sales_invoice_id UUID;
CREATE UNIQUE INDEX uq_sales_invoice_id_order ON public.sales_invoices(invoice_id,order_id);
ALTER TABLE public.deliveries ADD CONSTRAINT fk_delivery_sales_invoice_order
    FOREIGN KEY (sales_invoice_id,order_id) REFERENCES public.sales_invoices(invoice_id,order_id) ON DELETE RESTRICT;
CREATE INDEX idx_deliveries_sales_invoice ON public.deliveries(sales_invoice_id);
