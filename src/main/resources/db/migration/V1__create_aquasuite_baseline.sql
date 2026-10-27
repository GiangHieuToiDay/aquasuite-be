-- AquaSuite canonical empty-database baseline.
-- Squashed from the approved V1-V14 development chain on 2026-10-27.
-- This is intentionally not an upgrade path for databases with prior Flyway history.

-- ============================================================================
-- Source: V1__create_iam_foundation.sql
-- ============================================================================
-- V1__create_iam_foundation.sql
-- Baseline IAM Foundation Schema for AquaSuite IMS (Spec v0.4.0)

-- 1. Table: users
CREATE TABLE public.users (
    id UUID NOT NULL,
    username VARCHAR(100) NOT NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    avatar_url VARCHAR(2048) NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING_ACTIVATION',
    phone VARCHAR(32) NULL,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT chk_users_username_not_empty CHECK (length(username) > 0),
    CONSTRAINT chk_users_username_trimmed CHECK (username = btrim(username)),
    CONSTRAINT chk_users_email_not_empty CHECK (length(email) > 0),
    CONSTRAINT chk_users_email_trimmed CHECK (email = btrim(email)),
    CONSTRAINT chk_users_status CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'INACTIVE', 'LOCKED'))
);

CREATE UNIQUE INDEX uq_users_username_lower ON public.users (lower(username));
CREATE UNIQUE INDEX uq_users_email_lower ON public.users (lower(email));

-- 2. Table: roles
CREATE TABLE public.roles (
    role_id UUID NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(500) NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_roles PRIMARY KEY (role_id),
    CONSTRAINT chk_roles_role_name_not_empty CHECK (length(role_name) > 0)
);

CREATE UNIQUE INDEX uq_roles_role_name_lower ON public.roles (lower(role_name));

-- 3. Table: permissions
CREATE TABLE public.permissions (
    id UUID NOT NULL,
    code VARCHAR(150) NOT NULL,
    description VARCHAR(500) NULL,
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT chk_permissions_code_not_empty CHECK (length(code) > 0)
);

CREATE UNIQUE INDEX uq_permissions_code_lower ON public.permissions (lower(code));

-- 4. Table: user_roles (Spec v0.4.0: only user_id and role_id)
CREATE TABLE public.user_roles (
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES public.roles (role_id) ON DELETE RESTRICT
);

CREATE INDEX idx_user_roles_role_id ON public.user_roles (role_id);

-- 5. Table: role_permissions
CREATE TABLE public.role_permissions (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES public.roles (role_id) ON DELETE RESTRICT,
    CONSTRAINT fk_role_permissions_permission FOREIGN KEY (permission_id) REFERENCES public.permissions (id) ON DELETE RESTRICT
);

CREATE INDEX idx_role_permissions_permission_id ON public.role_permissions (permission_id);

-- ============================================================================
-- Source: V2__provision_canonical_roles_and_protection.sql
-- ============================================================================
-- V2__provision_canonical_roles_and_protection.sql
-- Provision canonical roles, validate permission/role invariants, and protect canonical roles from direct deletion/rename.

-- 1. Preflight existing permissions data
DO $$
DECLARE
    v_invalid_count INT;
    v_case_distinct_count INT;
    v_total_count INT;
BEGIN
    -- Check format: code must match ^[A-Z][A-Z0-9_]*$ and length 1-150
    SELECT COUNT(*) INTO v_invalid_count
    FROM public.permissions
    WHERE code !~ '^[A-Z][A-Z0-9_]*$' OR length(code) > 150;

    IF v_invalid_count > 0 THEN
        RAISE EXCEPTION 'Existing permissions contain % invalid code(s) not matching ^[A-Z][A-Z0-9_]*$', v_invalid_count;
    END IF;

    -- Check forbidden namespace: code cannot start with ROLE_ (case-insensitive)
    SELECT COUNT(*) INTO v_invalid_count
    FROM public.permissions
    WHERE upper(code) LIKE 'ROLE_%';

    IF v_invalid_count > 0 THEN
        RAISE EXCEPTION 'Existing permissions contain % code(s) starting with forbidden prefix ROLE_', v_invalid_count;
    END IF;

    -- Check case-insensitive uniqueness
    SELECT COUNT(DISTINCT lower(code)), COUNT(*) INTO v_case_distinct_count, v_total_count
    FROM public.permissions;

    IF v_case_distinct_count <> v_total_count THEN
        RAISE EXCEPTION 'Existing permissions contain case-insensitive duplicate codes';
    END IF;
END $$;

-- 1.1 Add database CHECK constraints on public.permissions
ALTER TABLE public.permissions
    ADD CONSTRAINT chk_permissions_code_format CHECK (code ~ '^[A-Z][A-Z0-9_]*$' AND char_length(code) <= 150),
    ADD CONSTRAINT chk_permissions_code_not_role_prefix CHECK (code !~* '^ROLE_');

-- 2. Preflight existing roles data
DO $$
DECLARE
    v_conflict_count INT;
    v_collision_count INT;
BEGIN
    -- Case-variant check: any role matching canonical names case-insensitively but not exact uppercase
    SELECT COUNT(*) INTO v_conflict_count
    FROM public.roles
    WHERE lower(role_name) IN ('shop_owner', 'sales_staff', 'accountant', 'technical_staff', 'shipper', 'customer')
      AND role_name NOT IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'TECHNICAL_STAFF', 'SHIPPER', 'CUSTOMER');

    IF v_conflict_count > 0 THEN
        RAISE EXCEPTION 'Existing roles contain % case-variant conflict(s) for canonical roles', v_conflict_count;
    END IF;

    -- Duplicate canonical roles
    SELECT COUNT(*) - COUNT(DISTINCT lower(role_name)) INTO v_conflict_count
    FROM public.roles
    WHERE role_name IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'TECHNICAL_STAFF', 'SHIPPER', 'CUSTOMER');

    IF v_conflict_count > 0 THEN
        RAISE EXCEPTION 'Existing roles contain duplicate canonical roles';
    END IF;

    -- UUID literal collision: literal UUID cannot be bound to a different role
    SELECT COUNT(*) INTO v_collision_count
    FROM public.roles
    WHERE (role_id = '10000000-0000-4000-8000-000000000001' AND role_name <> 'SHOP_OWNER')
       OR (role_id = '10000000-0000-4000-8000-000000000002' AND role_name <> 'SALES_STAFF')
       OR (role_id = '10000000-0000-4000-8000-000000000003' AND role_name <> 'ACCOUNTANT')
       OR (role_id = '10000000-0000-4000-8000-000000000004' AND role_name <> 'TECHNICAL_STAFF')
       OR (role_id = '10000000-0000-4000-8000-000000000005' AND role_name <> 'SHIPPER')
       OR (role_id = '10000000-0000-4000-8000-000000000006' AND role_name <> 'CUSTOMER');

    IF v_collision_count > 0 THEN
        RAISE EXCEPTION 'Existing role ID collision detected with canonical role UUID literals';
    END IF;
END $$;

-- 3. Provision missing canonical roles
INSERT INTO public.roles (role_id, role_name, description, is_active, created_at)
SELECT '10000000-0000-4000-8000-000000000001', 'SHOP_OWNER', 'Chủ cửa hàng', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.roles WHERE role_name = 'SHOP_OWNER');

INSERT INTO public.roles (role_id, role_name, description, is_active, created_at)
SELECT '10000000-0000-4000-8000-000000000002', 'SALES_STAFF', 'Nhân viên bán hàng', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.roles WHERE role_name = 'SALES_STAFF');

INSERT INTO public.roles (role_id, role_name, description, is_active, created_at)
SELECT '10000000-0000-4000-8000-000000000003', 'ACCOUNTANT', 'Kế toán', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.roles WHERE role_name = 'ACCOUNTANT');

INSERT INTO public.roles (role_id, role_name, description, is_active, created_at)
SELECT '10000000-0000-4000-8000-000000000004', 'TECHNICAL_STAFF', 'Nhân viên kỹ thuật', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.roles WHERE role_name = 'TECHNICAL_STAFF');

INSERT INTO public.roles (role_id, role_name, description, is_active, created_at)
SELECT '10000000-0000-4000-8000-000000000005', 'SHIPPER', 'Nhân viên giao hàng', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.roles WHERE role_name = 'SHIPPER');

INSERT INTO public.roles (role_id, role_name, description, is_active, created_at)
SELECT '10000000-0000-4000-8000-000000000006', 'CUSTOMER', 'Khách hàng', true, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM public.roles WHERE role_name = 'CUSTOMER');

-- 4. Trigger to protect canonical roles from direct deletion or rename
CREATE OR REPLACE FUNCTION public.prevent_canonical_role_modification()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.role_name IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'TECHNICAL_STAFF', 'SHIPPER', 'CUSTOMER') THEN
            RAISE EXCEPTION 'Cannot delete canonical role: %', OLD.role_name;
        END IF;
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.role_name IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'TECHNICAL_STAFF', 'SHIPPER', 'CUSTOMER') THEN
            IF OLD.role_name <> NEW.role_name THEN
                RAISE EXCEPTION 'Cannot rename canonical role: %', OLD.role_name;
            END IF;
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_protect_canonical_roles
BEFORE UPDATE OR DELETE ON public.roles
FOR EACH ROW
EXECUTE FUNCTION public.prevent_canonical_role_modification();

-- ============================================================================
-- Source: V2_1__create_inventory_baseline.sql
-- ============================================================================
CREATE TABLE public.inventories (
    inventory_id UUID PRIMARY KEY,
    variant_id UUID NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL DEFAULT 'WH_MAIN',
    on_hand_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    reserved_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    minimum_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    maximum_qty NUMERIC(12,2),
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_inventory_variant_warehouse UNIQUE (variant_id, warehouse_code),
    CONSTRAINT chk_inventory_non_negative CHECK (on_hand_qty >= 0 AND reserved_qty >= 0 AND minimum_qty >= 0)
);
CREATE INDEX idx_inv_variant_id ON public.inventories (variant_id);

CREATE TABLE public.stock_movements (
    movement_id UUID PRIMARY KEY,
    inventory_id UUID NOT NULL REFERENCES public.inventories(inventory_id),
    variant_id UUID NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity NUMERIC(12,2) NOT NULL,
    balance_after NUMERIC(12,2) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100),
    note TEXT,
    created_by UUID,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_movement_type CHECK (movement_type IN ('INITIAL_IMPORT','GOODS_RECEIPT','STOCK_DISPATCH','RETURN_IN','SUPPLIER_RETURN','ADJUSTMENT_IN','ADJUSTMENT_OUT'))
);
CREATE INDEX idx_movement_variant_created ON public.stock_movements (variant_id, created_at DESC);
CREATE INDEX idx_movement_inventory_id ON public.stock_movements (inventory_id);
CREATE INDEX idx_movement_reference ON public.stock_movements (reference_type, reference_id);

CREATE TABLE public.stock_counts (
    stock_count_id UUID PRIMARY KEY,
    stock_count_code VARCHAR(50) NOT NULL UNIQUE,
    warehouse_code VARCHAR(50) NOT NULL DEFAULT 'WH_MAIN',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    counted_by UUID,
    approved_by UUID,
    count_date TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_stock_count_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','COMPLETED','CANCELLED'))
);

CREATE TABLE public.stock_count_items (
    stock_count_item_id UUID PRIMARY KEY,
    stock_count_id UUID NOT NULL REFERENCES public.stock_counts(stock_count_id),
    inventory_id UUID NOT NULL REFERENCES public.inventories(inventory_id),
    variant_id UUID NOT NULL,
    system_qty NUMERIC(12,2) NOT NULL,
    actual_qty NUMERIC(12,2) NOT NULL,
    difference_qty NUMERIC(12,2) NOT NULL,
    reason VARCHAR(255)
);
CREATE INDEX idx_item_stock_count_id ON public.stock_count_items (stock_count_id);
CREATE INDEX idx_item_variant_id ON public.stock_count_items (variant_id);

CREATE TABLE public.suppliers (
    supplier_id UUID PRIMARY KEY,
    supplier_code VARCHAR(50) NOT NULL UNIQUE,
    supplier_name VARCHAR(255) NOT NULL,
    contact_name VARCHAR(150),
    phone VARCHAR(30), email VARCHAR(150), address TEXT, tax_code VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_supplier_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE public.supplier_products (
    supplier_id UUID NOT NULL REFERENCES public.suppliers(supplier_id),
    product_id UUID NOT NULL,
    supplier_sku VARCHAR(100),
    last_supply_price NUMERIC(15,2),
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (supplier_id, product_id)
);

-- ============================================================================
-- Source: V3__create_catalog_categories_and_brands.sql
-- ============================================================================
-- V3__create_catalog_categories_and_brands.sql
-- Schema migration for Catalog Category and Brand management (Spec v0.2.0)

-- 1. Preflight checks, provision catalog permissions and assign to SHOP_OWNER
DO $$
DECLARE
    v_shop_owner_id UUID;
    v_conflict_count INT;
BEGIN
    -- 1.1 Object preflight: fail-fast if migration-owned objects already exist
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'categories') THEN
        RAISE EXCEPTION 'Table categories already exists';
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'brands') THEN
        RAISE EXCEPTION 'Table brands already exists';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_proc p JOIN pg_namespace n ON p.pronamespace = n.oid WHERE n.nspname = 'public' AND p.proname = 'catalog_set_categories_updated_at') THEN
        RAISE EXCEPTION 'Function catalog_set_categories_updated_at already exists';
    END IF;

    IF EXISTS (SELECT 1 FROM pg_trigger WHERE tgname = 'trg_categories_set_updated_at') THEN
        RAISE EXCEPTION 'Trigger trg_categories_set_updated_at already exists';
    END IF;

    -- 1.2 Verify canonical SHOP_OWNER exists and is active
    SELECT role_id INTO v_shop_owner_id
    FROM public.roles
    WHERE role_name = 'SHOP_OWNER';

    IF v_shop_owner_id IS NULL THEN
        RAISE EXCEPTION 'SHOP_OWNER role is missing';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM public.roles WHERE role_id = v_shop_owner_id AND is_active = true) THEN
        RAISE EXCEPTION 'SHOP_OWNER role is inactive';
    END IF;

    -- 1.3 Temp table for catalog permissions
    CREATE TEMP TABLE tmp_catalog_perms (
        id UUID NOT NULL,
        code VARCHAR(150) NOT NULL,
        description VARCHAR(500) NOT NULL
    ) ON COMMIT DROP;

    INSERT INTO tmp_catalog_perms (id, code, description) VALUES
        ('20000000-0000-4000-8000-000000000001', 'CATALOG_CATEGORY_READ', 'Read catalog categories'),
        ('20000000-0000-4000-8000-000000000002', 'CATALOG_CATEGORY_CREATE', 'Create catalog categories'),
        ('20000000-0000-4000-8000-000000000003', 'CATALOG_CATEGORY_UPDATE', 'Update catalog categories'),
        ('20000000-0000-4000-8000-000000000004', 'CATALOG_CATEGORY_DELETE', 'Delete catalog categories'),
        ('20000000-0000-4000-8000-000000000005', 'CATALOG_BRAND_READ', 'Read catalog brands'),
        ('20000000-0000-4000-8000-000000000006', 'CATALOG_BRAND_CREATE', 'Create catalog brands'),
        ('20000000-0000-4000-8000-000000000007', 'CATALOG_BRAND_UPDATE', 'Update catalog brands'),
        ('20000000-0000-4000-8000-000000000008', 'CATALOG_BRAND_DELETE', 'Delete catalog brands');

    -- 1.4 Collision check: UUID matches but code or description differs
    SELECT COUNT(*) INTO v_conflict_count
    FROM public.permissions p
    JOIN tmp_catalog_perms t ON p.id = t.id
    WHERE p.code <> t.code OR p.description <> t.description;

    IF v_conflict_count > 0 THEN
        RAISE EXCEPTION 'UUID collision detected: existing permission ID matches catalog permission but with conflicting code or description';
    END IF;

    -- 1.5 Code conflict check: code matches (case-insensitively) but ID or description differs
    SELECT COUNT(*) INTO v_conflict_count
    FROM public.permissions p
    JOIN tmp_catalog_perms t ON lower(p.code) = lower(t.code)
    WHERE p.id <> t.id OR p.code <> t.code OR p.description <> t.description;

    IF v_conflict_count > 0 THEN
        RAISE EXCEPTION 'Code conflict detected: existing permission code matches catalog permission but with conflicting ID or description';
    END IF;

    -- 1.6 Insert missing permissions (compatible existing rows are retained)
    INSERT INTO public.permissions (id, code, description)
    SELECT t.id, t.code, t.description
    FROM tmp_catalog_perms t
    WHERE NOT EXISTS (
        SELECT 1 FROM public.permissions p WHERE p.id = t.id
    );

    -- 1.7 Idempotently assign permissions to canonical SHOP_OWNER
    INSERT INTO public.role_permissions (role_id, permission_id)
    SELECT v_shop_owner_id, t.id
    FROM tmp_catalog_perms t
    WHERE NOT EXISTS (
        SELECT 1 FROM public.role_permissions rp
        WHERE rp.role_id = v_shop_owner_id AND rp.permission_id = t.id
    );
END $$;

-- 2. Table: categories
CREATE TABLE public.categories (
    category_id UUID NOT NULL,
    category_code VARCHAR(50) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    parent_id UUID NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_categories PRIMARY KEY (category_id),
    CONSTRAINT fk_categories_parent FOREIGN KEY (parent_id) REFERENCES public.categories (category_id) ON DELETE RESTRICT,
    CONSTRAINT chk_categories_code_format CHECK (category_code ~ '^[A-Z0-9][A-Z0-9_]*$' AND char_length(category_code) <= 50),
    CONSTRAINT chk_categories_name_not_blank CHECK (btrim(category_name) <> '' AND char_length(category_name) <= 100),
    CONSTRAINT chk_categories_status CHECK (status IN ('ACTIVE', 'INACTIVE')),
    CONSTRAINT chk_categories_not_self_parent CHECK (parent_id IS NULL OR parent_id <> category_id)
);

CREATE UNIQUE INDEX uq_categories_code_lower ON public.categories (lower(btrim(category_code)));
CREATE UNIQUE INDEX uq_categories_root_name_lower ON public.categories (lower(btrim(category_name))) WHERE parent_id IS NULL;
CREATE UNIQUE INDEX uq_categories_parent_name_lower ON public.categories (parent_id, lower(btrim(category_name))) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_categories_parent_id ON public.categories (parent_id);
CREATE INDEX idx_categories_status ON public.categories (status);

-- 3. Category updated_at trigger function and trigger
CREATE FUNCTION public.catalog_set_categories_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.created_at = OLD.created_at;
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_categories_set_updated_at
BEFORE UPDATE ON public.categories
FOR EACH ROW
EXECUTE FUNCTION public.catalog_set_categories_updated_at();

-- 4. Table: brands
CREATE TABLE public.brands (
    brand_id UUID NOT NULL,
    brand_code VARCHAR(50) NOT NULL,
    brand_name VARCHAR(100) NOT NULL,
    description TEXT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_brands PRIMARY KEY (brand_id),
    CONSTRAINT chk_brands_code_format CHECK (brand_code ~ '^[A-Z0-9][A-Z0-9_]*$' AND char_length(brand_code) <= 50),
    CONSTRAINT chk_brands_name_not_blank CHECK (btrim(brand_name) <> '' AND char_length(brand_name) <= 100),
    CONSTRAINT chk_brands_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uq_brands_code_lower ON public.brands (lower(btrim(brand_code)));
CREATE UNIQUE INDEX uq_brands_name_lower ON public.brands (lower(btrim(brand_name)));
CREATE INDEX idx_brands_status ON public.brands (status);

-- ============================================================================
-- Source: V3_1__extend_iam_and_create_audit_log.sql
-- ============================================================================
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS provider VARCHAR(50) NOT NULL DEFAULT 'LOCAL',
    ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255),
    ADD COLUMN IF NOT EXISTS is_verified BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.user_roles
    ADD COLUMN IF NOT EXISTS assigned_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS assigned_by UUID;

DO $$
BEGIN
    IF EXISTS (
        SELECT phone
        FROM public.users
        WHERE phone IS NOT NULL
        GROUP BY phone
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot create uq_users_phone: duplicate non-null phone values exist';
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uq_users_phone ON public.users (phone) WHERE phone IS NOT NULL;

CREATE TABLE IF NOT EXISTS public.audit_logs (
    audit_id UUID NOT NULL,
    user_id UUID,
    entity_type VARCHAR(100) NOT NULL,
    entity_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,
    old_data JSONB,
    new_data JSONB,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_audit_logs PRIMARY KEY (audit_id)
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON public.audit_logs (entity_type, entity_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON public.audit_logs (user_id, created_at DESC);

-- ============================================================================
-- Source: V4__create_catalog_baseline.sql
-- ============================================================================
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

-- ============================================================================
-- Source: V5__add_variant_reference_documents.sql
-- ============================================================================
-- UC-10: tables required to retain variant usage history and clear abandoned carts.
-- V1-V4 remain immutable; these tables are additive.

CREATE TABLE public.goods_receipt_items (
    goods_receipt_item_id UUID PRIMARY KEY,
    goods_receipt_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_cost NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(15,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_goods_receipt_items_variant ON public.goods_receipt_items(variant_id);

CREATE TABLE public.quotation_items (
    quotation_item_id UUID PRIMARY KEY,
    quotation_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(15,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_quotation_items_variant ON public.quotation_items(variant_id);

CREATE TABLE public.order_items (
    order_item_id UUID PRIMARY KEY,
    order_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(15,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_order_items_variant ON public.order_items(variant_id);

CREATE TABLE public.stock_dispatch_items (
    dispatch_item_id UUID PRIMARY KEY,
    dispatch_id UUID,
    order_item_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    quantity NUMERIC(12,2) NOT NULL,
    serial_numbers JSONB
);
CREATE INDEX idx_stock_dispatch_items_variant ON public.stock_dispatch_items(variant_id);

CREATE TABLE public.sales_invoice_items (
    invoice_item_id UUID PRIMARY KEY,
    invoice_id UUID,
    order_item_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    description TEXT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(15,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_sales_invoice_items_variant ON public.sales_invoice_items(variant_id);

CREATE TABLE public.carts (
    cart_id UUID PRIMARY KEY,
    customer_id UUID,
    status VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.cart_items (
    cart_item_id UUID PRIMARY KEY,
    cart_id UUID REFERENCES public.carts(cart_id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_cart_items_variant ON public.cart_items(variant_id);

-- ============================================================================
-- Source: V6__create_crm_customer_and_delivery_addresses.sql
-- ============================================================================
-- V6__create_crm_customer_and_delivery_addresses.sql
-- SCRUM-5 / UC-03: Manage Profile and Delivery Addresses

-- 1. Bổ sung trường full_name vào bảng users (cho tài khoản nhân viên & định danh cá nhân)
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(150);

-- 2. Bảng customers: Hồ sơ khách hàng thương mại (Bảng 4 trong bản thiết kế)
CREATE TABLE IF NOT EXISTS public.customers (
    customer_id UUID NOT NULL,
    user_id UUID UNIQUE,
    customer_code VARCHAR(50) NOT NULL UNIQUE,
    customer_name VARCHAR(150) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    email VARCHAR(320),
    customer_type VARCHAR(32) NOT NULL DEFAULT 'RETAIL', -- RETAIL, CONTRACTOR, INSTALLER, PROJECT_CUSTOMER
    tax_code VARCHAR(50),
    company_name VARCHAR(255),
    address TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customers PRIMARY KEY (customer_id),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL,
    CONSTRAINT chk_customer_type CHECK (customer_type IN ('RETAIL', 'CONTRACTOR', 'INSTALLER', 'PROJECT_CUSTOMER')),
    CONSTRAINT chk_customer_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);

CREATE INDEX IF NOT EXISTS idx_customers_user_id ON public.customers (user_id);
CREATE INDEX IF NOT EXISTS idx_customers_phone ON public.customers (phone);

-- 3. Bảng customer_addresses: Sổ địa chỉ nhận hàng (Bảng 7 trong bản thiết kế)
CREATE TABLE IF NOT EXISTS public.customer_addresses (
    address_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    province VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    ward VARCHAR(100) NOT NULL,
    street_address VARCHAR(255) NOT NULL,
    address_text VARCHAR(500) NOT NULL,
    label VARCHAR(50) NOT NULL DEFAULT 'Home', -- Home, Construction Site, Villa, Office, etc.
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer_addresses PRIMARY KEY (address_id),
    CONSTRAINT fk_customer_addresses_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_addresses_customer ON public.customer_addresses (customer_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_customer_addresses_default ON public.customer_addresses (customer_id, is_default) WHERE is_default = true AND is_deleted = false;

-- ============================================================================
-- Source: V7__SCRUM14_phase1_combo_core.sql
-- ============================================================================
-- Flyway owns the transaction for preflight, DDL, backfill and verification.
DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM bundle_items WHERE quantity IS NULL OR quantity <= 0)
 OR EXISTS (SELECT 1 FROM bundle_items GROUP BY bundle_product_id,variant_id HAVING count(*)>1)
 OR EXISTS (SELECT 1 FROM bundle_items b LEFT JOIN products p ON p.product_id=b.bundle_product_id
            LEFT JOIN product_variants v ON v.variant_id=b.variant_id
            LEFT JOIN products vp ON vp.product_id=v.product_id
            WHERE p.product_id IS NULL OR p.product_type<>'BUNDLE' OR v.variant_id IS NULL OR vp.product_id IS NULL OR vp.product_type<>'NORMAL')
 OR EXISTS (SELECT 1 FROM product_variants v JOIN products p ON p.product_id=v.product_id WHERE p.product_type='BUNDLE')
 THEN RAISE EXCEPTION 'UC13 preflight failed: invalid, duplicate, orphan or nested bundle data. Correct reviewed data before retry.';
 END IF;
END $$;

-- Run only through Flyway; do not apply directly.
CREATE TABLE bundle_configurations (
 bundle_product_id UUID PRIMARY KEY REFERENCES products(product_id),
 package_price NUMERIC(15,2), valid_from TIMESTAMPTZ, valid_until TIMESTAMPTZ,
 publication_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 stock_policy VARCHAR(40) NOT NULL DEFAULT 'CHECK_COMPONENT_STOCK',
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT bundle_price_positive CHECK(package_price IS NULL OR package_price>0),
 CONSTRAINT bundle_validity CHECK ((valid_from IS NULL AND valid_until IS NULL) OR
   (valid_from IS NOT NULL AND valid_until IS NOT NULL AND valid_from<valid_until)),
 CONSTRAINT bundle_publication CHECK(publication_status IN ('DRAFT','PUBLISHED','UNPUBLISHED')),
 CONSTRAINT bundle_published_complete CHECK(publication_status<>'PUBLISHED' OR
   (package_price IS NOT NULL AND valid_from IS NOT NULL AND valid_until IS NOT NULL)),
 CONSTRAINT bundle_version_positive CHECK(version>=0)
);
INSERT INTO bundle_configurations(bundle_product_id)
 SELECT product_id FROM products WHERE product_type='BUNDLE';
-- Quantity, uniqueness and item foreign keys are already enforced by V4.
CREATE INDEX uc13_items_parent_idx ON bundle_items(bundle_product_id);
CREATE INDEX uc13_publication_idx ON bundle_configurations(publication_status);
-- Business audit is owned by audit_logs (V3.1); no unused audit_entries table.

DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM products p LEFT JOIN bundle_configurations c ON c.bundle_product_id=p.product_id
            WHERE p.product_type='BUNDLE' AND c.bundle_product_id IS NULL)
 OR EXISTS (SELECT 1 FROM bundle_configurations c JOIN products p ON p.product_id=c.bundle_product_id WHERE p.product_type<>'BUNDLE')
 THEN RAISE EXCEPTION 'UC13 verification failed: bundle configuration mapping is incomplete';
 END IF;
END $$;
SELECT 'UC13 schema verification passed' AS result;

-- ============================================================================
-- Source: V8__SCRUM14_phase2_combo_options.sql
-- ============================================================================
-- Additive migration; existing drafts and V1/V2 remain unchanged.
CREATE TABLE bundle_component_groups (
    bundle_product_id UUID NOT NULL REFERENCES bundle_configurations(bundle_product_id),
    group_id UUID NOT NULL,
    default_variant_id UUID NOT NULL REFERENCES product_variants(variant_id),
    allowed_variant_ids UUID[] NOT NULL,
    PRIMARY KEY (bundle_product_id, group_id),
    UNIQUE (bundle_product_id, default_variant_id),
    CHECK (cardinality(allowed_variant_ids) BETWEEN 1 AND 100),
    CHECK (array_position(allowed_variant_ids, NULL) IS NULL),
    CHECK (default_variant_id = ANY(allowed_variant_ids))
);

-- ============================================================================
-- Source: V9__password_recovery_storage.sql
-- ============================================================================
-- Preserve the mapped recovery table; develop has no legacy numeric accounts to backfill.
CREATE TABLE public.user_password_recovery (
    user_id UUID PRIMARY KEY REFERENCES public.users(id) ON DELETE CASCADE,
    verification_code VARCHAR(255),
    verification_expired_at TIMESTAMP,
    email_sent_date DATE,
    email_sent_count INTEGER,
    password_last_changed_at TIMESTAMP
);

-- ============================================================================
-- Source: V10__SCRUM08_registration_phone_preflight.sql
-- ============================================================================
-- Keep legacy account data intact; reconciliation is a human decision.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.users WHERE phone IS NOT NULL
               AND phone !~ '^0[35789][0-9]{8}$') THEN
        RAISE EXCEPTION 'Registration migration blocked: noncanonical phone values require reconciliation';
    END IF;
    IF EXISTS (SELECT 1 FROM public.users WHERE phone IS NOT NULL
               GROUP BY phone HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Registration migration blocked: duplicate phone values require reconciliation';
    END IF;
END $$;

-- full_name is owned by V6; unique phone index uq_users_phone is owned by V3.1.

-- ============================================================================
-- Source: V11__complete_canonical_schema.sql
-- ============================================================================
-- Complete fields mapped by current entities but absent from the reconciled V1-V10 chain.
-- The superseded numeric-ID and duplicate module baselines must not be restored.

ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS password_last_changed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS verification_code VARCHAR(100),
    ADD COLUMN IF NOT EXISTS fcm_token TEXT,
    ADD COLUMN IF NOT EXISTS verification_expired_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS email_sent_count INTEGER DEFAULT 0,
    ADD COLUMN IF NOT EXISTS email_sent_date DATE;

ALTER TABLE public.brands
    ADD COLUMN IF NOT EXISTS created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;

-- ============================================================================
-- Source: V12__create_sales_quotation_schema.sql
-- ============================================================================
-- Complete the quotation schema introduced as a minimal variant-reference table in V5.
-- AquaSuite uses UUID identifiers across IAM, CRM, catalog and sales boundaries.

CREATE TABLE public.quotations (
    quotation_id UUID NOT NULL,
    quotation_code VARCHAR(50) NOT NULL,
    revision_number INTEGER NOT NULL DEFAULT 1,
    parent_quotation_id UUID,
    customer_id UUID NOT NULL,
    project_name VARCHAR(255) NOT NULL,
    shipping_address TEXT,
    notes TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    quotation_date DATE NOT NULL,
    valid_until DATE NOT NULL,
    subtotal NUMERIC(18,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    net_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 10,
    tax_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    shipping_fee NUMERIC(18,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(18,2) NOT NULL DEFAULT 0,
    is_over_discount_limit BOOLEAN NOT NULL DEFAULT FALSE,
    estimated_margin_pct NUMERIC(5,2),
    delivery_schedule TEXT,
    pdf_file_url VARCHAR(500),
    sent_at TIMESTAMPTZ,
    sent_to_email VARCHAR(150),
    accepted_at TIMESTAMPTZ,
    signed_file_url VARCHAR(500),
    lost_reason VARCHAR(255),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_quotations PRIMARY KEY (quotation_id),
    CONSTRAINT uq_quotations_code UNIQUE (quotation_code),
    CONSTRAINT fk_quotations_parent FOREIGN KEY (parent_quotation_id)
        REFERENCES public.quotations (quotation_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quotations_customer FOREIGN KEY (customer_id)
        REFERENCES public.customers (customer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_quotations_created_by FOREIGN KEY (created_by)
        REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT chk_quotations_status CHECK (status IN (
        'DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'SENT',
        'ACCEPTED', 'LOST', 'EXPIRED', 'SUPERSEDED'
    ))
);

CREATE INDEX idx_quotations_customer_id ON public.quotations (customer_id);
CREATE INDEX idx_quotations_status ON public.quotations (status);
CREATE INDEX idx_quotations_parent_id ON public.quotations (parent_quotation_id);
CREATE INDEX idx_quotations_created_by ON public.quotations (created_by);

ALTER TABLE public.quotation_items
    ALTER COLUMN quotation_id SET NOT NULL,
    ALTER COLUMN variant_id DROP NOT NULL,
    ADD COLUMN sort_order INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN item_type VARCHAR(20) NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN parent_item_id UUID,
    ADD COLUMN combo_id UUID,
    ADD COLUMN sku_snapshot VARCHAR(50),
    ADD COLUMN item_name_snapshot VARCHAR(255) NOT NULL,
    ADD COLUMN cost_price_snapshot NUMERIC(18,2) NOT NULL DEFAULT 0,
    ADD COLUMN discount_percent NUMERIC(5,2) NOT NULL DEFAULT 0,
    ADD COLUMN net_unit_price NUMERIC(18,2) NOT NULL,
    ADD COLUMN available_stock_qty NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN incoming_stock_qty NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN backorder_qty NUMERIC(10,2) NOT NULL DEFAULT 0,
    ADD COLUMN lead_time_days INTEGER,
    ADD CONSTRAINT fk_quotation_items_quotation FOREIGN KEY (quotation_id)
        REFERENCES public.quotations (quotation_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_quotation_items_parent FOREIGN KEY (parent_item_id)
        REFERENCES public.quotation_items (quotation_item_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_quotation_items_combo FOREIGN KEY (combo_id)
        REFERENCES public.products (product_id) ON DELETE RESTRICT,
    ADD CONSTRAINT chk_quotation_items_type CHECK (item_type IN (
        'SINGLE', 'COMBO_HEADER', 'COMBO_COMPONENT'
    ));

CREATE INDEX idx_quotation_items_quotation_id ON public.quotation_items (quotation_id);
CREATE INDEX idx_quotation_items_parent_id ON public.quotation_items (parent_item_id);
CREATE INDEX idx_quotation_items_combo_id ON public.quotation_items (combo_id);
ALTER INDEX public.idx_quotation_items_variant RENAME TO idx_quotation_items_variant_id;

CREATE TABLE public.quotation_payment_milestones (
    milestone_id UUID NOT NULL,
    quotation_id UUID NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    percent NUMERIC(5,2) NOT NULL,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_quotation_payment_milestones PRIMARY KEY (milestone_id),
    CONSTRAINT fk_quotation_milestones_quotation FOREIGN KEY (quotation_id)
        REFERENCES public.quotations (quotation_id) ON DELETE CASCADE
);

CREATE INDEX idx_quotation_milestones_quotation_id
    ON public.quotation_payment_milestones (quotation_id);

-- ============================================================================
-- Source: V13__align_sales_schema_after_merge.sql
-- ============================================================================
-- Forward-only reconciliation for Sales entities merged after V12.
-- V11 and V12 are immutable because they are already recorded in deployed history.

-- Quotations: preserve the V12 table and adapt renamed/current entity fields.
ALTER TABLE public.quotations RENAME COLUMN revision_number TO revision_no;
ALTER TABLE public.quotations RENAME COLUMN shipping_address TO delivery_address;
ALTER TABLE public.quotations RENAME COLUMN notes TO note;
ALTER TABLE public.quotations RENAME COLUMN tax_rate TO vat_rate;
ALTER TABLE public.quotations RENAME COLUMN pdf_file_url TO pdf_url;

ALTER TABLE public.quotations
    ALTER COLUMN quotation_code TYPE VARCHAR(100),
    ALTER COLUMN status TYPE VARCHAR(32),
    ALTER COLUMN shipping_fee TYPE NUMERIC(15,2),
    ALTER COLUMN total_amount TYPE NUMERIC(15,2),
    ALTER COLUMN sent_to_email TYPE VARCHAR(320),
    ALTER COLUMN signed_file_url TYPE TEXT,
    ALTER COLUMN lost_reason TYPE TEXT,
    ADD COLUMN approved_by UUID,
    ADD COLUMN approved_at TIMESTAMPTZ,
    ADD COLUMN approval_note TEXT,
    ADD COLUMN margin_percent_at_approval NUMERIC(5,2),
    ADD COLUMN lost_at TIMESTAMPTZ;

CREATE UNIQUE INDEX IF NOT EXISTS uq_quotations_code_revision
    ON public.quotations (quotation_code, revision_no);
CREATE INDEX IF NOT EXISTS idx_quotations_date
    ON public.quotations (quotation_date);

-- Quotation items: reuse V5/V12 rows and map the current Sales snapshot names.
ALTER TABLE public.quotation_items RENAME COLUMN sku_snapshot TO sku;
ALTER TABLE public.quotation_items RENAME COLUMN cost_price_snapshot TO unit_cost;
ALTER TABLE public.quotation_items RENAME COLUMN discount_percent TO discount_rate;

ALTER TABLE public.quotation_items
    ALTER COLUMN sku TYPE VARCHAR(100),
    ALTER COLUMN unit_cost TYPE NUMERIC(15,2),
    ALTER COLUMN backorder_qty TYPE NUMERIC(12,2),
    ALTER COLUMN variant_id SET NOT NULL,
    ADD COLUMN unit VARCHAR(50);

-- Existing environments must explicitly reconcile incomplete quotation rows.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.quotation_items
        WHERE sku IS NULL OR unit IS NULL
    ) THEN
        RAISE EXCEPTION 'V13 preflight failed: quotation_items requires reviewed sku/unit backfill';
    END IF;
END $$;

ALTER TABLE public.quotation_items
    ALTER COLUMN sku SET NOT NULL,
    ALTER COLUMN unit SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_quotation_items_parent_item_id
    ON public.quotation_items (parent_item_id);

-- The merged cart entity is customer-scoped; retain the older carts table as
-- historical infrastructure while extending its empty item table safely.
ALTER TABLE public.cart_items
    ADD COLUMN customer_id UUID,
    ADD COLUMN updated_at TIMESTAMPTZ;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.cart_items
        WHERE customer_id IS NULL OR updated_at IS NULL
    ) THEN
        RAISE EXCEPTION 'V13 preflight failed: cart_items requires reviewed customer/timestamp backfill';
    END IF;
END $$;

ALTER TABLE public.cart_items
    ALTER COLUMN customer_id SET NOT NULL,
    ALTER COLUMN updated_at SET NOT NULL;

CREATE UNIQUE INDEX uq_cart_items_customer_variant
    ON public.cart_items (customer_id, variant_id);
CREATE INDEX idx_cart_items_customer_id
    ON public.cart_items (customer_id);

-- Orders and invoices are aggregate roots newly introduced by the merge.
CREATE TABLE public.orders (
    order_id UUID NOT NULL,
    order_code VARCHAR(100) NOT NULL,
    customer_id UUID NOT NULL,
    project_id UUID,
    quotation_id UUID,
    sales_staff_id UUID,
    promotion_id UUID,
    order_channel VARCHAR(32) NOT NULL,
    order_status VARCHAR(32) NOT NULL,
    payment_status VARCHAR(32) NOT NULL,
    order_date TIMESTAMPTZ NOT NULL,
    due_date DATE,
    subtotal NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    delivery_address TEXT,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_orders PRIMARY KEY (order_id),
    CONSTRAINT uq_orders_order_code UNIQUE (order_code),
    CONSTRAINT fk_orders_customer FOREIGN KEY (customer_id)
        REFERENCES public.customers (customer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_quotation FOREIGN KEY (quotation_id)
        REFERENCES public.quotations (quotation_id) ON DELETE RESTRICT,
    CONSTRAINT fk_orders_sales_staff FOREIGN KEY (sales_staff_id)
        REFERENCES public.users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_orders_customer_id ON public.orders (customer_id);
CREATE INDEX idx_orders_project_id ON public.orders (project_id);
CREATE INDEX idx_orders_quotation_id ON public.orders (quotation_id);
CREATE INDEX idx_orders_sales_staff_id ON public.orders (sales_staff_id);
CREATE INDEX idx_orders_order_status ON public.orders (order_status);
CREATE INDEX idx_orders_payment_status ON public.orders (payment_status);
CREATE INDEX idx_orders_order_date ON public.orders (order_date);

ALTER TABLE public.order_items
    ALTER COLUMN order_id SET NOT NULL,
    ADD CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
        REFERENCES public.orders (order_id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_order_items_order_id
    ON public.order_items (order_id);
ALTER INDEX public.idx_order_items_variant RENAME TO idx_order_items_variant_id;

CREATE TABLE public.sales_invoices (
    invoice_id UUID NOT NULL,
    invoice_code VARCHAR(100) NOT NULL,
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    invoice_type VARCHAR(32) NOT NULL,
    issue_date TIMESTAMPTZ NOT NULL,
    subtotal NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    tax_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_sales_invoices PRIMARY KEY (invoice_id),
    CONSTRAINT uq_sales_invoices_invoice_code UNIQUE (invoice_code),
    CONSTRAINT fk_sales_invoices_order FOREIGN KEY (order_id)
        REFERENCES public.orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_invoices_customer FOREIGN KEY (customer_id)
        REFERENCES public.customers (customer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_sales_invoices_created_by FOREIGN KEY (created_by)
        REFERENCES public.users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_sales_invoices_order_id ON public.sales_invoices (order_id);
CREATE INDEX idx_sales_invoices_customer_id ON public.sales_invoices (customer_id);
CREATE INDEX idx_sales_invoices_status ON public.sales_invoices (status);
CREATE INDEX idx_sales_invoices_issue_date ON public.sales_invoices (issue_date);

ALTER TABLE public.sales_invoice_items
    ALTER COLUMN invoice_id SET NOT NULL,
    ALTER COLUMN order_item_id SET NOT NULL,
    ADD CONSTRAINT fk_sales_invoice_items_invoice FOREIGN KEY (invoice_id)
        REFERENCES public.sales_invoices (invoice_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_sales_invoice_items_order_item FOREIGN KEY (order_item_id)
        REFERENCES public.order_items (order_item_id) ON DELETE RESTRICT;

CREATE INDEX IF NOT EXISTS idx_sales_invoice_items_invoice_id
    ON public.sales_invoice_items (invoice_id);
CREATE INDEX IF NOT EXISTS idx_sales_invoice_items_order_item_id
    ON public.sales_invoice_items (order_item_id);
ALTER INDEX public.idx_sales_invoice_items_variant RENAME TO idx_sales_invoice_items_variant_id;

-- Purchasing/receiving entities were merged with the Sales work and also need
-- a forward schema from the V5 goods-receipt placeholder.
CREATE TABLE public.purchase_orders (
    purchase_order_id UUID NOT NULL,
    po_code VARCHAR(100) NOT NULL,
    supplier_id UUID NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    order_date DATE NOT NULL,
    expected_date DATE,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    note TEXT,
    created_by UUID NOT NULL,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancelled_at TIMESTAMPTZ,
    cancel_reason TEXT,
    closed_by UUID,
    closed_at TIMESTAMPTZ,
    close_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_purchase_orders PRIMARY KEY (purchase_order_id),
    CONSTRAINT uq_purchase_orders_code UNIQUE (po_code),
    CONSTRAINT fk_purchase_orders_supplier FOREIGN KEY (supplier_id)
        REFERENCES public.suppliers (supplier_id) ON DELETE RESTRICT
);

CREATE INDEX idx_po_supplier_id ON public.purchase_orders (supplier_id);
CREATE INDEX idx_po_status ON public.purchase_orders (status);
CREATE INDEX idx_po_order_date ON public.purchase_orders (order_date);

CREATE TABLE public.purchase_order_items (
    purchase_order_item_id UUID NOT NULL,
    purchase_order_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    unit VARCHAR(50) NOT NULL,
    quantity_ordered NUMERIC(12,2) NOT NULL,
    quantity_received NUMERIC(12,2) NOT NULL DEFAULT 0,
    unit_cost NUMERIC(15,2) NOT NULL,
    line_total NUMERIC(15,2) NOT NULL,
    CONSTRAINT pk_purchase_order_items PRIMARY KEY (purchase_order_item_id),
    CONSTRAINT uq_po_items_po_variant UNIQUE (purchase_order_id, variant_id),
    CONSTRAINT fk_po_items_order FOREIGN KEY (purchase_order_id)
        REFERENCES public.purchase_orders (purchase_order_id) ON DELETE CASCADE,
    CONSTRAINT fk_po_items_variant FOREIGN KEY (variant_id)
        REFERENCES public.product_variants (variant_id) ON DELETE RESTRICT
);

CREATE INDEX idx_po_items_po_id ON public.purchase_order_items (purchase_order_id);
CREATE INDEX idx_po_items_variant_id ON public.purchase_order_items (variant_id);

CREATE TABLE public.goods_receipts (
    goods_receipt_id UUID NOT NULL,
    receipt_code VARCHAR(100) NOT NULL,
    purchase_order_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    receipt_date DATE NOT NULL,
    supplier_invoice_no VARCHAR(100),
    invoice_image_url TEXT,
    total_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    note TEXT,
    created_by UUID NOT NULL,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    cancelled_by UUID,
    cancelled_at TIMESTAMPTZ,
    cancel_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_goods_receipts PRIMARY KEY (goods_receipt_id),
    CONSTRAINT uq_goods_receipts_code UNIQUE (receipt_code),
    CONSTRAINT fk_goods_receipts_order FOREIGN KEY (purchase_order_id)
        REFERENCES public.purchase_orders (purchase_order_id) ON DELETE RESTRICT
);

CREATE INDEX idx_gr_po_id ON public.goods_receipts (purchase_order_id);
CREATE INDEX idx_gr_status ON public.goods_receipts (status);
CREATE INDEX idx_gr_receipt_date ON public.goods_receipts (receipt_date);

ALTER TABLE public.goods_receipt_items
    ADD COLUMN purchase_order_item_id UUID,
    ADD COLUMN unit VARCHAR(50);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.goods_receipt_items
        WHERE goods_receipt_id IS NULL OR purchase_order_item_id IS NULL OR unit IS NULL
    ) THEN
        RAISE EXCEPTION 'V13 preflight failed: goods_receipt_items requires reviewed receiving backfill';
    END IF;
END $$;

ALTER TABLE public.goods_receipt_items
    ALTER COLUMN goods_receipt_id SET NOT NULL,
    ALTER COLUMN purchase_order_item_id SET NOT NULL,
    ALTER COLUMN unit SET NOT NULL,
    ADD CONSTRAINT fk_gr_items_receipt FOREIGN KEY (goods_receipt_id)
        REFERENCES public.goods_receipts (goods_receipt_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_gr_items_po_item FOREIGN KEY (purchase_order_item_id)
        REFERENCES public.purchase_order_items (purchase_order_item_id) ON DELETE RESTRICT;

CREATE UNIQUE INDEX uq_goods_receipt_items_receipt_po_item
    ON public.goods_receipt_items (goods_receipt_id, purchase_order_item_id);
CREATE INDEX idx_goods_receipt_items_receipt_id
    ON public.goods_receipt_items (goods_receipt_id);
CREATE INDEX idx_goods_receipt_items_po_item_id
    ON public.goods_receipt_items (purchase_order_item_id);

-- ============================================================================
-- Source: V14__create_remaining_module_tables.sql
-- ============================================================================
-- Create entity-owned tables introduced without migrations by the merged work.

CREATE TABLE public.ai_chat_sessions (
    session_id UUID NOT NULL,
    customer_id UUID,
    user_id UUID,
    session_type VARCHAR(50) NOT NULL,
    messages JSONB NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT pk_ai_chat_sessions PRIMARY KEY (session_id)
);
CREATE INDEX idx_ai_chat_sessions_customer_id ON public.ai_chat_sessions (customer_id);
CREATE INDEX idx_ai_chat_sessions_user_id ON public.ai_chat_sessions (user_id);
CREATE INDEX idx_ai_chat_sessions_started_at ON public.ai_chat_sessions (started_at);

CREATE TABLE public.deliveries (
    delivery_id UUID NOT NULL,
    delivery_code VARCHAR(100) NOT NULL,
    order_id UUID NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL,
    shipper_id UUID,
    batch_no INTEGER,
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    dispatch_date TIMESTAMPTZ,
    scheduled_date TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    recipient_name VARCHAR(255) NOT NULL,
    recipient_phone VARCHAR(32) NOT NULL,
    delivery_address TEXT NOT NULL,
    cod_required BOOLEAN NOT NULL DEFAULT FALSE,
    cod_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_deliveries PRIMARY KEY (delivery_id),
    CONSTRAINT uq_deliveries_code UNIQUE (delivery_code),
    CONSTRAINT fk_deliveries_order FOREIGN KEY (order_id)
        REFERENCES public.orders (order_id) ON DELETE RESTRICT
);
CREATE INDEX idx_deliveries_order_id ON public.deliveries (order_id);
CREATE INDEX idx_deliveries_warehouse_code ON public.deliveries (warehouse_code);
CREATE INDEX idx_deliveries_shipper_id ON public.deliveries (shipper_id);
CREATE INDEX idx_deliveries_delivery_status ON public.deliveries (delivery_status);

CREATE TABLE public.delivery_items (
    delivery_item_id UUID NOT NULL,
    delivery_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    inventory_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity NUMERIC(12,2) NOT NULL,
    serial_numbers JSONB,
    CONSTRAINT pk_delivery_items PRIMARY KEY (delivery_item_id),
    CONSTRAINT fk_delivery_items_delivery FOREIGN KEY (delivery_id)
        REFERENCES public.deliveries (delivery_id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_items_order_item FOREIGN KEY (order_item_id)
        REFERENCES public.order_items (order_item_id) ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_items_inventory FOREIGN KEY (inventory_id)
        REFERENCES public.inventories (inventory_id) ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_items_variant FOREIGN KEY (variant_id)
        REFERENCES public.product_variants (variant_id) ON DELETE RESTRICT
);
CREATE INDEX idx_delivery_items_delivery_id ON public.delivery_items (delivery_id);
CREATE INDEX idx_delivery_items_order_item_id ON public.delivery_items (order_item_id);
CREATE INDEX idx_delivery_items_inventory_id ON public.delivery_items (inventory_id);
CREATE INDEX idx_delivery_items_variant_id ON public.delivery_items (variant_id);

CREATE TABLE public.transactions (
    transaction_id UUID NOT NULL,
    transaction_code VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    payment_type VARCHAR(32),
    category VARCHAR(100) NOT NULL,
    customer_id UUID,
    supplier_id UUID,
    order_id UUID,
    goods_receipt_id UUID,
    invoice_id UUID,
    amount NUMERIC(15,2) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    reference_no VARCHAR(255),
    proof_url TEXT,
    transaction_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    created_by UUID NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transactions PRIMARY KEY (transaction_id),
    CONSTRAINT uq_transactions_code UNIQUE (transaction_code)
);
CREATE INDEX idx_transactions_type ON public.transactions (transaction_type);
CREATE INDEX idx_transactions_customer_id ON public.transactions (customer_id);
CREATE INDEX idx_transactions_supplier_id ON public.transactions (supplier_id);
CREATE INDEX idx_transactions_order_id ON public.transactions (order_id);
CREATE INDEX idx_transactions_goods_receipt_id ON public.transactions (goods_receipt_id);
CREATE INDEX idx_transactions_invoice_id ON public.transactions (invoice_id);
CREATE INDEX idx_transactions_status ON public.transactions (status);
CREATE INDEX idx_transactions_date ON public.transactions (transaction_date);

CREATE TABLE public.warranty_records (
    warranty_record_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    serial_number VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    warranty_terms TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_warranty_records PRIMARY KEY (warranty_record_id)
);
CREATE INDEX idx_warranty_records_customer_id ON public.warranty_records (customer_id);
CREATE INDEX idx_warranty_records_order_item_id ON public.warranty_records (order_item_id);
CREATE INDEX idx_warranty_records_variant_id ON public.warranty_records (variant_id);
CREATE INDEX idx_warranty_records_serial_number ON public.warranty_records (serial_number);
CREATE INDEX idx_warranty_records_status ON public.warranty_records (status);

CREATE TABLE public.technical_jobs (
    technical_job_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    order_id UUID,
    warranty_record_id UUID,
    technician_id UUID,
    created_by UUID NOT NULL,
    job_type VARCHAR(32) NOT NULL,
    service_address TEXT NOT NULL,
    issue_description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    result TEXT,
    technical_note TEXT,
    rejection_reason TEXT,
    scheduled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_technical_jobs PRIMARY KEY (technical_job_id)
);
CREATE INDEX idx_technical_jobs_customer_id ON public.technical_jobs (customer_id);
CREATE INDEX idx_technical_jobs_order_id ON public.technical_jobs (order_id);
CREATE INDEX idx_technical_jobs_warranty_record_id ON public.technical_jobs (warranty_record_id);
CREATE INDEX idx_technical_jobs_technician_id ON public.technical_jobs (technician_id);
CREATE INDEX idx_technical_jobs_job_type ON public.technical_jobs (job_type);
CREATE INDEX idx_technical_jobs_status ON public.technical_jobs (status);
CREATE INDEX idx_technical_jobs_scheduled_at ON public.technical_jobs (scheduled_at);

