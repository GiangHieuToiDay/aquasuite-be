ALTER TABLE order_items ADD COLUMN IF NOT EXISTS combo_group_id UUID;
ALTER TABLE order_items ADD COLUMN IF NOT EXISTS combo_quantity NUMERIC(12,2);
