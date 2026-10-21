-- Category and brand tables are owned by V3; this migration adds products and SKUs.

CREATE TABLE public.products (
    product_id UUID NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    category_id UUID NOT NULL,
    brand_id UUID,
    product_name VARCHAR(255) NOT NULL,
    description TEXT,
    specifications JSONB,
    product_type VARCHAR(50) NOT NULL DEFAULT 'NORMAL',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_products PRIMARY KEY (product_id),
    CONSTRAINT uq_products_product_code UNIQUE (product_code),
    CONSTRAINT fk_products_category FOREIGN KEY (category_id) REFERENCES public.categories (category_id) ON DELETE RESTRICT,
    CONSTRAINT fk_products_brand FOREIGN KEY (brand_id) REFERENCES public.brands (brand_id) ON DELETE RESTRICT,
    CONSTRAINT chk_products_product_type CHECK (product_type IN ('NORMAL', 'BUNDLE')),
    CONSTRAINT chk_products_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

CREATE INDEX idx_products_category_id ON public.products (category_id);
CREATE INDEX idx_products_brand_id ON public.products (brand_id);

CREATE TABLE public.product_variants (
    variant_id UUID NOT NULL,
    product_id UUID NOT NULL,
    sku VARCHAR(100) NOT NULL,
    variant_name VARCHAR(255) NOT NULL,
    unit VARCHAR(50) NOT NULL,
    conversion_rate NUMERIC(12,4) NOT NULL DEFAULT 1,
    color VARCHAR(100),
    size VARCHAR(100),
    weight NUMERIC(10,3) NOT NULL DEFAULT 0,
    technical_spec JSONB,
    cost_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    selling_price NUMERIC(15,2),
    warranty_policy_id UUID,
    serial_tracking BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product_variants PRIMARY KEY (variant_id),
    CONSTRAINT uq_product_variants_sku UNIQUE (sku),
    CONSTRAINT fk_product_variants_product FOREIGN KEY (product_id) REFERENCES public.products (product_id) ON DELETE RESTRICT,
    CONSTRAINT chk_product_variants_conversion_rate CHECK (conversion_rate > 0),
    CONSTRAINT chk_product_variants_weight CHECK (weight >= 0),
    CONSTRAINT chk_product_variants_cost_price CHECK (cost_price >= 0),
    CONSTRAINT chk_product_variants_selling_price CHECK (selling_price IS NULL OR selling_price >= 0),
    CONSTRAINT chk_product_variants_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'DELETED'))
);

CREATE INDEX idx_product_variants_product_id ON public.product_variants (product_id);

CREATE TABLE public.product_images (
    image_id UUID NOT NULL,
    product_id UUID NOT NULL,
    variant_id UUID,
    image_url TEXT NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    sort_order INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_product_images PRIMARY KEY (image_id),
    CONSTRAINT fk_product_images_product FOREIGN KEY (product_id) REFERENCES public.products (product_id) ON DELETE RESTRICT,
    CONSTRAINT fk_product_images_variant FOREIGN KEY (variant_id) REFERENCES public.product_variants (variant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_product_images_sort_order CHECK (sort_order >= 0)
);

CREATE INDEX idx_product_images_product_id ON public.product_images (product_id);
CREATE INDEX idx_product_images_variant_id ON public.product_images (variant_id);

CREATE TABLE public.bundle_items (
    bundle_item_id UUID NOT NULL,
    bundle_product_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_bundle_items PRIMARY KEY (bundle_item_id),
    CONSTRAINT uq_bundle_items_bundle_variant UNIQUE (bundle_product_id, variant_id),
    CONSTRAINT fk_bundle_items_bundle_product FOREIGN KEY (bundle_product_id) REFERENCES public.products (product_id) ON DELETE RESTRICT,
    CONSTRAINT fk_bundle_items_variant FOREIGN KEY (variant_id) REFERENCES public.product_variants (variant_id) ON DELETE RESTRICT,
    CONSTRAINT chk_bundle_items_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_bundle_items_variant_id ON public.bundle_items (variant_id);
