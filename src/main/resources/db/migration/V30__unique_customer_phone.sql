-- Preserve all customer/order identities. Do not silently merge ambiguous historical profiles.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM public.customers WHERE phone <> btrim(phone)) THEN
        RAISE EXCEPTION 'Customer phone linking blocked: reconcile surrounding phone whitespace before migration';
    END IF;
    IF EXISTS (SELECT 1 FROM public.customers GROUP BY phone HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Customer phone linking blocked: reconcile duplicate customer phones before migration';
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_customers_phone ON public.customers (phone);
