CREATE TABLE public.sales_reservation_releases (
    decision_id UUID PRIMARY KEY REFERENCES public.order_modification_requests(modification_request_id),
    order_id UUID NOT NULL REFERENCES public.orders(order_id),
    executed_by UUID NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    result JSONB NOT NULL
);
CREATE INDEX idx_sales_reservation_releases_order ON public.sales_reservation_releases(order_id);
