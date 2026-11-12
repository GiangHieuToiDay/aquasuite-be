-- UC29 existing-order slice; selections are immutable until a future approved amendment flow.
CREATE TABLE public.sales_reservation_requests (
    order_id UUID PRIMARY KEY REFERENCES public.orders(order_id) ON DELETE RESTRICT,
    selection TEXT NOT NULL,
    pricing TEXT NOT NULL,
    reservation_value NUMERIC(15,2) NOT NULL CHECK (reservation_value > 0),
    actor_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE public.sales_reservation_operations (
    request_id UUID PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES public.orders(order_id) ON DELETE RESTRICT,
    payload TEXT NOT NULL,
    actor_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
