-- V6: Update purchase order schema for UC-34 two-phase draft & revision flow

ALTER TABLE public.purchase_orders ADD COLUMN IF NOT EXISTS version INT NOT NULL DEFAULT 0;
ALTER TABLE public.purchase_orders ADD COLUMN IF NOT EXISTS warehouse_id UUID;
ALTER TABLE public.purchase_orders ALTER COLUMN warehouse_code DROP NOT NULL;

ALTER TABLE public.purchase_order_items ALTER COLUMN unit_cost DROP NOT NULL;
ALTER TABLE public.purchase_order_items ALTER COLUMN line_total DROP NOT NULL;
ALTER TABLE public.purchase_order_items ADD COLUMN IF NOT EXISTS sku VARCHAR(100);
ALTER TABLE public.purchase_order_items ADD COLUMN IF NOT EXISTS product_name VARCHAR(255);
ALTER TABLE public.purchase_order_items ADD COLUMN IF NOT EXISTS sort_order INT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_po_items_sort_order ON public.purchase_order_items (purchase_order_id, sort_order);