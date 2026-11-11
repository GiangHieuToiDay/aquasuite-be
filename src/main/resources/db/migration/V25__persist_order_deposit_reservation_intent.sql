-- Only explicit future requests are marked; historical intent cannot be inferred from payments.
ALTER TABLE public.orders
    ADD COLUMN reserve_stock_after_deposit BOOLEAN NOT NULL DEFAULT FALSE;
