ALTER TABLE public.transactions ADD COLUMN idempotency_key UUID;
CREATE UNIQUE INDEX uq_transactions_idempotency_key ON public.transactions (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
