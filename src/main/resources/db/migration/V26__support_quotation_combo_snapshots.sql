-- Combo roots contain package pricing; component rows keep real ProductVariant foreign keys.
ALTER TABLE public.quotation_items
    ADD COLUMN IF NOT EXISTS combo_id UUID REFERENCES public.products (product_id) ON DELETE RESTRICT,
    ADD COLUMN IF NOT EXISTS item_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN IF NOT EXISTS combo_name VARCHAR(255),
    ALTER COLUMN variant_id DROP NOT NULL;

CREATE INDEX IF NOT EXISTS idx_quotation_items_combo_id ON public.quotation_items (combo_id);

ALTER TABLE public.quotation_items
    DROP CONSTRAINT IF EXISTS chk_quotation_item_product_kind,
    ADD CONSTRAINT chk_quotation_item_product_kind CHECK (
        variant_id IS NOT NULL
        OR (combo_id IS NOT NULL AND parent_item_id IS NULL
            AND combo_name IS NOT NULL)
    ) NOT VALID;
