CREATE TABLE public.order_payment_proofs (
    payment_id UUID PRIMARY KEY REFERENCES public.transactions(transaction_id) ON DELETE RESTRICT,
    mime_type VARCHAR(32) NOT NULL,
    content BYTEA NOT NULL,
    uploaded_by UUID NOT NULL REFERENCES public.users(id) ON DELETE RESTRICT,
    uploaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_order_payment_proof_size CHECK (octet_length(content) BETWEEN 1 AND 5242880),
    CONSTRAINT chk_order_payment_proof_type CHECK (mime_type IN ('image/png', 'image/jpeg', 'application/pdf'))
);
