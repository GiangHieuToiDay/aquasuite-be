-- Cho phép giỏ hàng khách hàng (cart_items) lưu trực tiếp gói Combo (combo_id -> products.product_id)
-- song song với biến thể lẻ (variant_id -> product_variants.variant_id).

ALTER TABLE public.cart_items
    ALTER COLUMN variant_id DROP NOT NULL,
    ADD COLUMN IF NOT EXISTS combo_id UUID REFERENCES public.products(product_id) ON DELETE RESTRICT;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'chk_cart_items_variant_or_combo'
    ) THEN
        ALTER TABLE public.cart_items
            ADD CONSTRAINT chk_cart_items_variant_or_combo
                CHECK (num_nonnulls(variant_id, combo_id) = 1);
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cart_items_customer_combo
    ON public.cart_items (customer_id, combo_id)
    WHERE combo_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_cart_items_combo_id
    ON public.cart_items (combo_id)
    WHERE combo_id IS NOT NULL;
