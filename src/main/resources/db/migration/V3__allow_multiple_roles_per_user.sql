-- UC-05 restores the original IAM many-to-many role model.
-- V2 remains immutable for databases that already recorded it; this migration
-- removes only the single-role constraint introduced by V2.
ALTER TABLE public.user_roles
    DROP CONSTRAINT IF EXISTS uq_user_roles_user_id;
