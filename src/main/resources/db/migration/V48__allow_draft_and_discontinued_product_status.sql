-- Allow all ProductStatus enum values (DRAFT, ACTIVE, INACTIVE, DISCONTINUED, DELETED) on public.products.
ALTER TABLE public.products
    DROP CONSTRAINT IF EXISTS chk_products_status;

ALTER TABLE public.products
    ADD CONSTRAINT chk_products_status
    CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'DISCONTINUED', 'DELETED'));

