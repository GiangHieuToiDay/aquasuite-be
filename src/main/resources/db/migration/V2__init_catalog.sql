-- ====================================================================
-- V2: Module Catalog
-- Entities:
--   - Category (categories)
--   - Brand (brands)
--   - Product (products)
--   - ProductVariant (product_variants)
--   - ProductImage (product_images)
--   - BundleItem (bundle_items)
-- ====================================================================

-- 1. Categories
CREATE TABLE IF NOT EXISTS categories (
    category_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    category_code VARCHAR(100) NOT NULL UNIQUE,
    category_name VARCHAR(255) NOT NULL,
    parent_id UUID REFERENCES categories(category_id) ON DELETE SET NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_categories_code ON categories(category_code);
CREATE INDEX IF NOT EXISTS idx_categories_parent ON categories(parent_id);

-- 2. Brands
CREATE TABLE IF NOT EXISTS brands (
    brand_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    brand_code VARCHAR(100) NOT NULL UNIQUE,
    brand_name VARCHAR(255) NOT NULL,
    description TEXT,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_brands_code ON brands(brand_code);

-- 3. Products
CREATE TABLE IF NOT EXISTS products (
    product_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_code VARCHAR(100) NOT NULL UNIQUE,
    category_id UUID NOT NULL REFERENCES categories(category_id) ON DELETE RESTRICT,
    brand_id UUID REFERENCES brands(brand_id) ON DELETE SET NULL,
    product_name VARCHAR(255) NOT NULL,
    description TEXT,
    specifications JSONB,
    product_type VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_products_code ON products(product_code);
CREATE INDEX IF NOT EXISTS idx_products_category ON products(category_id);
CREATE INDEX IF NOT EXISTS idx_products_brand ON products(brand_id);

-- 4. Product Variants
CREATE TABLE IF NOT EXISTS product_variants (
    variant_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    sku VARCHAR(100) NOT NULL UNIQUE,
    variant_name VARCHAR(255) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    conversion_rate NUMERIC(12, 4) NOT NULL DEFAULT 1,
    color VARCHAR(100),
    size VARCHAR(100),
    weight NUMERIC(10, 3) NOT NULL DEFAULT 0,
    technical_spec JSONB,
    cost_price NUMERIC(15, 2) NOT NULL DEFAULT 0,
    selling_price NUMERIC(15, 2) NOT NULL,
    warranty_policy_id UUID,
    serial_tracking BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_variants_sku ON product_variants(sku);
CREATE INDEX IF NOT EXISTS idx_variants_product ON product_variants(product_id);

-- 5. Product Images
CREATE TABLE IF NOT EXISTS product_images (
    image_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    variant_id UUID REFERENCES product_variants(variant_id) ON DELETE CASCADE,
    image_url TEXT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_images_product ON product_images(product_id);
CREATE INDEX IF NOT EXISTS idx_images_variant ON product_images(variant_id);

-- 6. Bundle Items
CREATE TABLE IF NOT EXISTS bundle_items (
    bundle_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    bundle_product_id UUID NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES product_variants(variant_id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS idx_bundle_product ON bundle_items(bundle_product_id);
CREATE INDEX IF NOT EXISTS idx_bundle_variant ON bundle_items(variant_id);
