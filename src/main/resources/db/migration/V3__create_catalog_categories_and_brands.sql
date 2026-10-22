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
