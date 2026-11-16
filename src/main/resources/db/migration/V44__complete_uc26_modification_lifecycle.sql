ALTER TABLE public.order_modification_requests
    ADD COLUMN requested_delivery_address TEXT,
    ADD COLUMN refund_transaction_id UUID;

ALTER TABLE public.order_modification_requests
    ADD CONSTRAINT fk_order_modification_requests_refund_transaction
        FOREIGN KEY (refund_transaction_id)
        REFERENCES public.transactions (transaction_id) ON DELETE RESTRICT;

CREATE INDEX idx_order_modification_requests_refund_transaction_id
    ON public.order_modification_requests (refund_transaction_id);
