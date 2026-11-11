-- UC-29 inventory-first slice. No order/payment workflow is activated here.
ALTER TABLE public.orders
    ADD COLUMN paid_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    ADD CONSTRAINT chk_orders_paid_amount CHECK (paid_amount >= 0);

-- Source IDs intentionally remain scalar: the caller owns document validation.
CREATE TABLE public.stock_reservations (
    reservation_id UUID PRIMARY KEY,
    inventory_id UUID NOT NULL REFERENCES public.inventories(inventory_id) ON DELETE RESTRICT,
    source_type VARCHAR(32) NOT NULL CHECK (source_type = 'ORDER'),
    source_id UUID NOT NULL,
    source_line_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL,
    requested_qty NUMERIC(12,2) NOT NULL CHECK (requested_qty > 0),
    held_qty NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (held_qty >= 0),
    consumed_qty NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (consumed_qty >= 0),
    released_qty NUMERIC(12,2) NOT NULL DEFAULT 0 CHECK (released_qty >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_stock_reservation_source_inventory UNIQUE(source_type, source_id, source_line_id, inventory_id),
    CONSTRAINT chk_stock_reservation_target CHECK (held_qty + consumed_qty + released_qty <= requested_qty)
);
CREATE INDEX idx_stock_reservations_source ON public.stock_reservations(source_type, source_id, source_line_id);
CREATE INDEX idx_stock_reservations_variant ON public.stock_reservations(variant_id);

-- Ledger is also the immutable reservation history; it is not a physical stock movement.
CREATE TABLE public.stock_reservation_operations (
    request_id UUID PRIMARY KEY,
    reservation_id UUID NOT NULL REFERENCES public.stock_reservations(reservation_id) ON DELETE RESTRICT,
    action VARCHAR(16) NOT NULL CHECK (action IN ('RESERVE', 'RELEASE')),
    payload TEXT NOT NULL,
    result JSONB NOT NULL,
    held_before NUMERIC(12,2) NOT NULL,
    held_after NUMERIC(12,2) NOT NULL,
    actor_id UUID NOT NULL,
    reason VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_stock_reservation_operations_history ON public.stock_reservation_operations(reservation_id, created_at);
