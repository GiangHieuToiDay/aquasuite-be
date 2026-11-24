-- Loại bỏ vai trò TECHNICAL_STAFF (nhóm quyết định 2026-11-24: bỏ role Nhân viên kỹ thuật và các UC FE-13 liên quan).
-- Bảng technical_jobs được giữ lại vì hồ sơ thẻ bảo hành (UC-45) vẫn đọc các phiếu sửa chữa bảo hành đã có.

-- 1. Bỏ TECHNICAL_STAFF khỏi danh sách vai trò gốc được trigger bảo vệ (giữ nguyên 5 vai trò còn lại).
CREATE OR REPLACE FUNCTION public.prevent_canonical_role_modification()
RETURNS TRIGGER AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        IF OLD.role_name IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'SHIPPER', 'CUSTOMER') THEN
            RAISE EXCEPTION 'Cannot delete canonical role: %', OLD.role_name;
        END IF;
        RETURN OLD;
    ELSIF TG_OP = 'UPDATE' THEN
        IF OLD.role_name IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'SHIPPER', 'CUSTOMER') THEN
            IF OLD.role_name <> NEW.role_name THEN
                RAISE EXCEPTION 'Cannot rename canonical role: %', OLD.role_name;
            END IF;
        END IF;
        RETURN NEW;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

-- 2. Gỡ vai trò khỏi các tài khoản và bỏ các quyền đã gán cho vai trò (FK ON DELETE RESTRICT).
--    Tài khoản chỉ có vai trò này sẽ không còn vai trò nào; Chủ cửa hàng gán lại vai trò khác nếu cần.
DELETE FROM public.user_roles
WHERE role_id IN (SELECT role_id FROM public.roles WHERE role_name = 'TECHNICAL_STAFF');

DELETE FROM public.role_permissions
WHERE role_id IN (SELECT role_id FROM public.roles WHERE role_name = 'TECHNICAL_STAFF');

-- 3. Xóa vai trò.
DELETE FROM public.roles WHERE role_name = 'TECHNICAL_STAFF';
