-- These legacy columns have no mapping or consumer in the quotation implementation.
ALTER TABLE public.quotation_items
    DROP COLUMN IF EXISTS item_name_snapshot,
    DROP COLUMN IF EXISTS net_unit_price;
