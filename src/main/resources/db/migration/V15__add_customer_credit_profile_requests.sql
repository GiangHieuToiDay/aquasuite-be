-- UC-24: durable approval workflow for customer credit-profile changes.
-- Active profile values remain on customers; this table owns only requested/review state.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM public.customers
        WHERE credit_limit > 500000000.00
    ) THEN
        RAISE EXCEPTION 'Customer credit profiles above 500,000,000 VND require remediation before V15';
    END IF;
END $$;

ALTER TABLE public.customers
    DROP CONSTRAINT IF EXISTS chk_customers_credit_profile,
    ADD CONSTRAINT chk_customers_credit_profile CHECK (
        (credit_limit = 0 AND credit_term_days IS NULL)
        OR (
            credit_limit > 0
            AND credit_limit <= 500000000.00
            AND credit_term_days IS NOT NULL
            AND credit_term_days BETWEEN 1 AND 30
        )
    );

CREATE TABLE IF NOT EXISTS public.customer_credit_profile_requests (
    request_id UUID PRIMARY KEY,
    customer_id UUID NOT NULL REFERENCES public.customers(customer_id) ON DELETE RESTRICT,
    requester_id UUID NOT NULL,
    requested_credit_limit NUMERIC(15, 2) NOT NULL
        CHECK (requested_credit_limit > 0 AND requested_credit_limit <= 500000000.00),
    requested_credit_term_days INTEGER NOT NULL
        CHECK (requested_credit_term_days BETWEEN 1 AND 30),
    reason VARCHAR(500) NOT NULL CHECK (length(trim(reason)) > 0),
    identity_document_number VARCHAR(32),
    residential_address VARCHAR(500),
    worksite_address VARCHAR(500),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    review_note VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_credit_profile_request_review CHECK (
        (status = 'PENDING' AND reviewed_by IS NULL AND reviewed_at IS NULL)
        OR (status = 'APPROVED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)
        OR (status = 'REJECTED' AND reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL
            AND review_note IS NOT NULL AND length(trim(review_note)) > 0)
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_customer_credit_profile_request_pending
    ON public.customer_credit_profile_requests (customer_id)
    WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS idx_customer_credit_profile_request_status_created
    ON public.customer_credit_profile_requests (status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_customer_credit_profile_request_customer_created
    ON public.customer_credit_profile_requests (customer_id, created_at DESC);
