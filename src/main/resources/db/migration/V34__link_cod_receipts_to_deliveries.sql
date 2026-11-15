-- COD uses the existing transactions table; no additional business tables.
ALTER TABLE public.transactions
    ADD COLUMN delivery_id UUID REFERENCES public.deliveries(delivery_id) ON DELETE RESTRICT;
CREATE INDEX idx_transactions_delivery_id ON public.transactions(delivery_id);
ALTER TABLE public.transactions ADD CONSTRAINT chk_cod_delivery_receipt CHECK (
    delivery_id IS NULL OR (
        transaction_type = 'RECEIPT' AND payment_type IS NOT DISTINCT FROM 'COD'
        AND payment_method = 'CASH' AND order_id IS NOT NULL AND customer_id IS NOT NULL
        AND idempotency_key IS NOT NULL AND amount > 0 AND amount = trunc(amount)
        AND status IN ('PENDING','VERIFIED','REJECTED')
        AND (status = 'PENDING' OR (approved_by IS NOT NULL AND approved_at IS NOT NULL AND approved_by <> created_by))
        AND (status <> 'REJECTED' OR (reject_reason IS NOT NULL AND length(trim(reject_reason)) > 0))
    )
);

CREATE OR REPLACE FUNCTION public.guard_open_cod_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.total_amount IS DISTINCT FROM OLD.total_amount
        OR NEW.customer_id IS DISTINCT FROM OLD.customer_id
        OR (NEW.order_status IS DISTINCT FROM OLD.order_status AND NEW.order_status IN ('CANCELLED','PENDING_CANCEL')))
        AND EXISTS (SELECT 1 FROM public.transactions t WHERE t.order_id = OLD.order_id
            AND t.delivery_id IS NOT NULL AND t.payment_type = 'COD' AND t.status = 'PENDING') THEN
        RAISE EXCEPTION 'Resolve pending COD before changing this order' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS guard_open_cod_order ON public.orders;
CREATE TRIGGER guard_open_cod_order BEFORE UPDATE ON public.orders
    FOR EACH ROW EXECUTE FUNCTION public.guard_open_cod_order();

CREATE OR REPLACE FUNCTION public.guard_cod_receipt_balance() RETURNS trigger LANGUAGE plpgsql AS $$
DECLARE
    order_total NUMERIC;
    paid NUMERIC;
    pending_cod NUMERIC;
BEGIN
    IF NEW.order_id IS NULL OR NEW.transaction_type <> 'RECEIPT'
       OR NOT (NEW.status = 'VERIFIED' OR (NEW.delivery_id IS NOT NULL AND NEW.payment_type = 'COD' AND NEW.status = 'PENDING')) THEN
        RETURN NEW;
    END IF;
    SELECT total_amount INTO order_total FROM public.orders WHERE order_id = NEW.order_id FOR UPDATE;
    SELECT COALESCE(sum(amount),0) INTO paid FROM public.transactions
        WHERE order_id = NEW.order_id AND transaction_type = 'RECEIPT' AND status = 'VERIFIED'
        AND transaction_id <> NEW.transaction_id;
    SELECT COALESCE(sum(amount),0) INTO pending_cod FROM public.transactions
        WHERE order_id = NEW.order_id AND transaction_type = 'RECEIPT' AND payment_type = 'COD'
        AND delivery_id IS NOT NULL AND status = 'PENDING' AND transaction_id <> NEW.transaction_id;
    IF NEW.amount > GREATEST(order_total - paid - pending_cod,0) THEN
        RAISE EXCEPTION 'Receipt exceeds available balance after pending COD' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS guard_cod_receipt_balance ON public.transactions;
CREATE TRIGGER guard_cod_receipt_balance BEFORE INSERT OR UPDATE ON public.transactions
    FOR EACH ROW EXECUTE FUNCTION public.guard_cod_receipt_balance();
