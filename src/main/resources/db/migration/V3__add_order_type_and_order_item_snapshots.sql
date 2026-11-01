-- V3__add_order_type_and_order_item_snapshots.sql
-- Add order_type to orders and snapshot columns to order_items

-- 1. Bổ sung order_type cho bảng orders
ALTER TABLE public.orders 
    ADD COLUMN IF NOT EXISTS order_type VARCHAR(32) NOT NULL DEFAULT 'RETAIL';

CREATE INDEX IF NOT EXISTS idx_orders_order_type ON public.orders (order_type);

-- 2. Bổ sung các cột snapshot và combo_id cho bảng order_items
ALTER TABLE public.order_items 
    ADD COLUMN IF NOT EXISTS sku VARCHAR(100),
    ADD COLUMN IF NOT EXISTS product_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS variant_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS combo_id UUID;

CREATE INDEX IF NOT EXISTS idx_order_items_combo_id ON public.order_items (combo_id);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_order_items_combo'
    ) THEN
        ALTER TABLE public.order_items
            ADD CONSTRAINT fk_order_items_combo FOREIGN KEY (combo_id)
            REFERENCES public.products (product_id) ON DELETE SET NULL;
    END IF;
END $$;
