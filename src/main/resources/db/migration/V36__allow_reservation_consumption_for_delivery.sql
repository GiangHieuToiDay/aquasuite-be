-- Additive UC39 support: reuse existing reservation ledger, no new tables.
ALTER TABLE public.stock_reservation_operations
    DROP CONSTRAINT stock_reservation_operations_action_check;
ALTER TABLE public.stock_reservation_operations
    ADD CONSTRAINT stock_reservation_operations_action_check CHECK (action IN ('RESERVE','RELEASE','CONSUME'));
CREATE UNIQUE INDEX uq_delivery_order_batch ON public.deliveries(order_id,batch_no) WHERE batch_no IS NOT NULL;
