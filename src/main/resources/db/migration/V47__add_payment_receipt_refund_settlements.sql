-- UC-63/UC-64: a verified refund consumes the original receipt balance and must not re-enter allocation.
CREATE TABLE public.payment_receipt_refund_settlements (
    settlement_id UUID NOT NULL,
    receipt_transaction_id UUID NOT NULL,
    refund_transaction_id UUID NOT NULL,
    source_order_id UUID NOT NULL,
    settled_amount NUMERIC(15, 2) NOT NULL,
    settled_by UUID NOT NULL,
    settled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reason TEXT NOT NULL,
    CONSTRAINT pk_payment_receipt_refund_settlements PRIMARY KEY (settlement_id),
    CONSTRAINT chk_payment_receipt_refund_settlements_amount_positive CHECK (settled_amount > 0),
    CONSTRAINT fk_payment_receipt_refund_settlements_receipt FOREIGN KEY (receipt_transaction_id)
        REFERENCES public.transactions (transaction_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_receipt_refund_settlements_refund FOREIGN KEY (refund_transaction_id)
        REFERENCES public.transactions (transaction_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_receipt_refund_settlements_order FOREIGN KEY (source_order_id)
        REFERENCES public.orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_receipt_refund_settlements_actor FOREIGN KEY (settled_by)
        REFERENCES public.users (id) ON DELETE RESTRICT
);

CREATE INDEX idx_payment_receipt_refund_settlements_receipt
    ON public.payment_receipt_refund_settlements (receipt_transaction_id);
CREATE INDEX idx_payment_receipt_refund_settlements_refund
    ON public.payment_receipt_refund_settlements (refund_transaction_id);
