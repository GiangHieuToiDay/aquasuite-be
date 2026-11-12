ALTER TABLE public.sales_reservation_requests ADD COLUMN warehouse_code VARCHAR(50);

-- Recover the warehouse only from the original, persisted selection confirmation.
UPDATE public.sales_reservation_requests r
SET warehouse_code = (
    SELECT CASE WHEN count(DISTINCT (o.payload::jsonb ->> 2)) = 1
                THEN min(o.payload::jsonb ->> 2) ELSE NULL END
    FROM public.sales_reservation_operations o
    WHERE o.order_id = r.order_id AND o.payload::jsonb ->> 1 = 'REQUEST'
);

CREATE TABLE public.sales_reservation_allocations (
    allocation_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES public.orders(order_id) ON DELETE RESTRICT,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','DONE','FAILED')),
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    error_code VARCHAR(64),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_sales_reservation_allocations_due
    ON public.sales_reservation_allocations(next_attempt_at) WHERE status IN ('PENDING','FAILED');
CREATE INDEX idx_sales_reservation_allocations_order
    ON public.sales_reservation_allocations(order_id,created_at DESC);
