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
