-- V43: Hỗ trợ Đặt hàng Trực tuyến (UC-18) và tích hợp Thanh toán PayOS
ALTER TABLE public.orders
    ADD COLUMN IF NOT EXISTS payment_method VARCHAR(32);

CREATE TABLE IF NOT EXISTS public.online_payos_payments (
    payos_payment_id UUID NOT NULL,
    order_id UUID NOT NULL,
    payos_order_code BIGINT NOT NULL,
    payment_link_id VARCHAR(128) NOT NULL,
    checkout_url TEXT NOT NULL,
    qr_code TEXT,
    bin VARCHAR(32),
    account_number VARCHAR(64),
    account_name VARCHAR(255),
    amount BIGINT NOT NULL,
    amount_paid BIGINT NOT NULL DEFAULT 0,
    description VARCHAR(255),
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_online_payos_payments PRIMARY KEY (payos_payment_id),
    CONSTRAINT uq_online_payos_order_id UNIQUE (order_id),
    CONSTRAINT uq_online_payos_order_code UNIQUE (payos_order_code),
    CONSTRAINT fk_online_payos_order FOREIGN KEY (order_id)
        REFERENCES public.orders (order_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_online_payos_link_id
    ON public.online_payos_payments (payment_link_id);
CREATE INDEX IF NOT EXISTS idx_online_payos_status
    ON public.online_payos_payments (status);

