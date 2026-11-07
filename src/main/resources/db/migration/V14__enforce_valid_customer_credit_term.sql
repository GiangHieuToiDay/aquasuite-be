-- Preserve existing data: stop migration for owner remediation rather than
-- inventing a credit term for customers with an incomplete credit profile.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM public.customers
        WHERE credit_limit IS NULL OR credit_limit < 0
           OR (credit_limit = 0 AND credit_term_days IS NOT NULL)
           OR (credit_limit > 0 AND (credit_term_days IS NULL OR credit_term_days NOT BETWEEN 1 AND 30))
    ) THEN
        RAISE EXCEPTION 'Invalid customer credit profiles require remediation before V14';
    END IF;
END $$;

ALTER TABLE public.customers
    DROP CONSTRAINT chk_customers_credit_profile,
    ADD CONSTRAINT chk_customers_credit_profile CHECK (
        (credit_limit = 0 AND credit_term_days IS NULL)
        OR (credit_limit > 0 AND credit_term_days IS NOT NULL AND credit_term_days BETWEEN 1 AND 30)
    );
