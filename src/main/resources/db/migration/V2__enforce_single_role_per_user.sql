-- UC-05: one assigned role is allowed for each user account.
-- Do not silently discard existing roles. Operators must resolve multi-role data
-- explicitly before this constraint can be applied.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.user_roles
        GROUP BY user_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION
            'Cannot enforce one role per user: existing user_roles contains users with multiple roles. Resolve the data before retrying migration.';
    END IF;
END $$;

ALTER TABLE public.user_roles
    ADD CONSTRAINT uq_user_roles_user_id UNIQUE (user_id);
