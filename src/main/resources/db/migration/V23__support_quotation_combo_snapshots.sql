-- Combo roots contain package pricing; component rows keep real ProductVariant foreign keys.
ALTER TABLE public.quotation_items
    ALTER COLUMN variant_id DROP NOT NULL,
    ADD COLUMN combo_name VARCHAR(255),
    ADD CONSTRAINT chk_quotation_item_product_kind CHECK (
        variant_id IS NOT NULL
        OR (combo_id IS NOT NULL AND parent_item_id IS NULL
            AND combo_name IS NOT NULL)
    ) NOT VALID;
