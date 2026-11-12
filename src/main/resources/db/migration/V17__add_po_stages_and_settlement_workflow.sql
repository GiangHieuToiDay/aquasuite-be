-- Migration V17: Add PO stages and pay-on-delivery settlement workflow

-- 1. Bổ sung các cột điều phối đợt vào purchase_orders
ALTER TABLE public.purchase_orders
    ADD COLUMN IF NOT EXISTS delivery_plan_type VARCHAR(32) NOT NULL DEFAULT 'SINGLE',
    ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(15,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS is_short_closed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS reopened_by UUID,
    ADD COLUMN IF NOT EXISTS reopened_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS reopen_reason TEXT;

CREATE INDEX IF NOT EXISTS idx_po_is_short_closed ON public.purchase_orders (is_short_closed);

-- 2. Tạo bảng đợt giao hàng và thanh toán (purchase_order_stages)
CREATE TABLE IF NOT EXISTS public.purchase_order_stages (
    stage_id UUID PRIMARY KEY,
    purchase_order_id UUID NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    description TEXT,
    expected_delivery_date DATE NOT NULL,
    percent NUMERIC(5,2) NOT NULL,
    amount NUMERIC(15,2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    actual_amount NUMERIC(15,2),
    paid_at TIMESTAMPTZ,
    paid_by UUID,
    payment_method VARCHAR(50),
    payment_reference VARCHAR(255),
    settlement_note TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_po_stages_po FOREIGN KEY (purchase_order_id)
        REFERENCES public.purchase_orders (purchase_order_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_po_stages_po_id ON public.purchase_order_stages (purchase_order_id);
CREATE INDEX IF NOT EXISTS idx_po_stages_status ON public.purchase_order_stages (status);

-- 3. Tạo bảng yêu cầu hoàn tác đợt (purchase_order_stage_rollback_requests)
CREATE TABLE IF NOT EXISTS public.purchase_order_stage_rollback_requests (
    rollback_id UUID PRIMARY KEY,
    stage_id UUID NOT NULL,
    purchase_order_id UUID NOT NULL,
    requested_by UUID NOT NULL,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    request_reason TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reviewed_by UUID,
    reviewed_at TIMESTAMPTZ,
    review_note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_posrr_stage FOREIGN KEY (stage_id)
        REFERENCES public.purchase_order_stages (stage_id) ON DELETE CASCADE,
    CONSTRAINT fk_posrr_po FOREIGN KEY (purchase_order_id)
        REFERENCES public.purchase_orders (purchase_order_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_posrr_stage_id ON public.purchase_order_stage_rollback_requests (stage_id);
CREATE INDEX IF NOT EXISTS idx_posrr_status ON public.purchase_order_stage_rollback_requests (status);

