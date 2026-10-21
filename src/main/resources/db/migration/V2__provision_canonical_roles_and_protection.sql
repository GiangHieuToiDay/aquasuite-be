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
