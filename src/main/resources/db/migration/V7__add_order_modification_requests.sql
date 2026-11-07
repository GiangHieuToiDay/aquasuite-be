CREATE TABLE public.order_modification_requests (
    modification_request_id UUID NOT NULL,
    order_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    request_type VARCHAR(32) NOT NULL,
    request_status VARCHAR(32) NOT NULL,
    customer_reason TEXT NOT NULL,
    decision_note TEXT,
    requested_by UUID NOT NULL,
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    replacement_order_id UUID,
    original_invoice_id UUID,
    resulting_invoice_id UUID,
    tax_result_reference VARCHAR(100),
    price_difference NUMERIC(15, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_modification_requests PRIMARY KEY (modification_request_id),
    CONSTRAINT fk_order_modification_requests_order FOREIGN KEY (order_id)
        REFERENCES public.orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_modification_requests_customer FOREIGN KEY (customer_id)
        REFERENCES public.customers (customer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_modification_requests_requested_by FOREIGN KEY (requested_by)
        REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_modification_requests_reviewed_by FOREIGN KEY (reviewed_by)
        REFERENCES public.users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_modification_requests_replacement_order FOREIGN KEY (replacement_order_id)
        REFERENCES public.orders (order_id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_modification_requests_original_invoice FOREIGN KEY (original_invoice_id)
        REFERENCES public.sales_invoices (invoice_id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_modification_requests_resulting_invoice FOREIGN KEY (resulting_invoice_id)
        REFERENCES public.sales_invoices (invoice_id) ON DELETE RESTRICT
);

CREATE INDEX idx_order_modification_requests_order_id
    ON public.order_modification_requests (order_id);
CREATE INDEX idx_order_modification_requests_customer_id
    ON public.order_modification_requests (customer_id);
CREATE INDEX idx_order_modification_requests_status
    ON public.order_modification_requests (request_status);
CREATE INDEX idx_order_modification_requests_created_at
    ON public.order_modification_requests (created_at DESC);
CREATE UNIQUE INDEX uq_order_modification_requests_active_order
    ON public.order_modification_requests (order_id)
    WHERE request_status IN ('PENDING_REVIEW', 'PENDING_CANCEL');

CREATE TABLE public.order_modification_request_items (
    modification_request_item_id UUID NOT NULL,
    modification_request_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    original_variant_id UUID NOT NULL,
    requested_variant_id UUID,
    change_action VARCHAR(16) NOT NULL,
    original_quantity NUMERIC(12, 2) NOT NULL,
    requested_quantity NUMERIC(12, 2),
    unit_price_snapshot NUMERIC(15, 2) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_order_modification_request_items PRIMARY KEY (modification_request_item_id),
    CONSTRAINT fk_order_modification_request_items_request FOREIGN KEY (modification_request_id)
        REFERENCES public.order_modification_requests (modification_request_id) ON DELETE CASCADE,
    CONSTRAINT fk_order_modification_request_items_order_item FOREIGN KEY (order_item_id)
        REFERENCES public.order_items (order_item_id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_modification_request_items_original_variant FOREIGN KEY (original_variant_id)
        REFERENCES public.product_variants (variant_id) ON DELETE RESTRICT,
    CONSTRAINT fk_order_modification_request_items_requested_variant FOREIGN KEY (requested_variant_id)
        REFERENCES public.product_variants (variant_id) ON DELETE RESTRICT,
    CONSTRAINT ck_order_modification_request_items_quantity
        CHECK (original_quantity > 0 AND (requested_quantity IS NULL OR requested_quantity >= 0))
);

CREATE INDEX idx_order_modification_request_items_request_id
    ON public.order_modification_request_items (modification_request_id);
CREATE INDEX idx_order_modification_request_items_order_item_id
    ON public.order_modification_request_items (order_item_id);
