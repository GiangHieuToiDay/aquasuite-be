-- UC-63: persist how a verified customer receipt settles issued electronic invoices.
-- The receipt transaction itself remains immutable and is never duplicated by allocation.

CREATE TABLE public.payment_invoice_allocations (
    allocation_id UUID NOT NULL,
    allocation_request_id UUID NOT NULL,
    transaction_id UUID NOT NULL,
    order_id UUID NOT NULL,
    invoice_id UUID NOT NULL,
    allocated_amount NUMERIC(15, 2) NOT NULL,
    allocation_type VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    allocation_status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    reason TEXT,
    allocated_by UUID NOT NULL,
    allocated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reversed_by UUID,
    reversed_at TIMESTAMPTZ,
    reversal_reason TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_payment_invoice_allocations PRIMARY KEY (allocation_id),
    CONSTRAINT chk_payment_invoice_allocations_amount_positive CHECK (allocated_amount > 0),
    CONSTRAINT chk_payment_invoice_allocations_type CHECK (allocation_type IN ('MANUAL', 'AUTO_ADVANCE', 'REPOINTED')),
    CONSTRAINT chk_payment_invoice_allocations_status CHECK (allocation_status IN ('ACTIVE', 'REVERSED')),
    CONSTRAINT fk_payment_invoice_allocations_transaction FOREIGN KEY (transaction_id)
        REFERENCES public.transactions (transaction_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_invoice_allocations_order FOREIGN KEY (order_id)
        REFERENCES public.orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_invoice_allocations_invoice FOREIGN KEY (invoice_id)
        REFERENCES public.sales_invoices (invoice_id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_invoice_allocations_allocated_by FOREIGN KEY (allocated_by)
        REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_payment_invoice_allocations_reversed_by FOREIGN KEY (reversed_by)
        REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT uq_payment_invoice_allocations_request_invoice UNIQUE (transaction_id, allocation_request_id, invoice_id)
);

CREATE INDEX idx_payment_invoice_allocations_transaction_active
    ON public.payment_invoice_allocations (transaction_id)
    WHERE allocation_status = 'ACTIVE';

CREATE INDEX idx_payment_invoice_allocations_invoice_active
    ON public.payment_invoice_allocations (invoice_id)
    WHERE allocation_status = 'ACTIVE';

CREATE INDEX idx_payment_invoice_allocations_order_active
    ON public.payment_invoice_allocations (order_id)
    WHERE allocation_status = 'ACTIVE';
