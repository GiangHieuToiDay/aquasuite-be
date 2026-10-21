-- UC-10 development/demo seed: one ACTIVE master product for Variant & SKU API tests.
-- Run against a development or local test database only.
-- It is idempotent for the three explicit demo business codes below.
-- It intentionally creates NO product_variants; create them through the UC-10 API.

BEGIN;

WITH seeded_brand AS (
    INSERT INTO public.brands (brand_id, brand_code, brand_name, description, status)
    VALUES (
        '10000000-0000-0000-0000-000000000001',
        'TOTO-UC10-DEMO',
        'TOTO',
        'Brand seed used only by the UC-10 local API demo.',
        'ACTIVE'
    )
    ON CONFLICT (brand_code) DO UPDATE
    SET brand_name = EXCLUDED.brand_name,
        description = EXCLUDED.description,
        status = EXCLUDED.status,
        updated_at = CURRENT_TIMESTAMP
    RETURNING brand_id
), seeded_category AS (
    INSERT INTO public.categories (category_id, category_code, category_name, description, status)
    VALUES (
        '10000000-0000-0000-0000-000000000002',
        'SANITARY-UC10-DEMO',
        'Thiết bị vệ sinh',
        'Category seed used only by the UC-10 local API demo.',
        'ACTIVE'
    )
    ON CONFLICT (category_code) DO UPDATE
    SET category_name = EXCLUDED.category_name,
        description = EXCLUDED.description,
        status = EXCLUDED.status,
        updated_at = CURRENT_TIMESTAMP
    RETURNING category_id
), seeded_product AS (
    INSERT INTO public.products (
        product_id,
        product_code,
        category_id,
        brand_id,
        product_name,
        description,
        specifications,
        product_type,
        status
    )
    SELECT
        '10000000-0000-0000-0000-000000000003',
        'PRD-UC10-NEOREST-DEMO',
        seeded_category.category_id,
        seeded_brand.brand_id,
        'TOTO Neorest Smart Toilet - UC-10 Demo',
        'Master product for testing SKU variants, technical specifications, and pricing.',
        jsonb_build_object(
            'productLine', 'Neorest',
            'productType', 'Smart Toilet',
            'demoPurpose', 'UC-10'
        ),
        'NORMAL',
        'ACTIVE'
    FROM seeded_brand
    CROSS JOIN seeded_category
    ON CONFLICT (product_code) DO UPDATE
    SET category_id = EXCLUDED.category_id,
        brand_id = EXCLUDED.brand_id,
        product_name = EXCLUDED.product_name,
        description = EXCLUDED.description,
        specifications = EXCLUDED.specifications,
        product_type = EXCLUDED.product_type,
        status = EXCLUDED.status,
        updated_at = CURRENT_TIMESTAMP
    RETURNING product_id, product_code, product_name, status
)
SELECT product_id, product_code, product_name, status
FROM seeded_product;

COMMIT;

-- Expected productId for Postman if no existing demo row was present:
-- 10000000-0000-0000-0000-000000000003
