-- V13: Add quotation terms and payment milestones to orders

ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS quotation_code VARCHAR(100);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS project_name VARCHAR(255);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS delivery_schedule TEXT;
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS vat_rate NUMERIC(5,2);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS shipping_fee NUMERIC(15,2);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS deposit_percent NUMERIC(5,2);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS deposit_amount NUMERIC(15,2);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS credit_term_days INT;
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS remaining_debt_amount NUMERIC(15,2);
ALTER TABLE public.orders ADD COLUMN IF NOT EXISTS debt_condition TEXT;

CREATE TABLE IF NOT EXISTS public.order_payment_milestones (
    milestone_id UUID NOT NULL,
    order_id UUID NOT NULL,
    stage_name VARCHAR(100) NOT NULL,
    percent NUMERIC(5,2) NOT NULL,
    amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT pk_order_payment_milestones PRIMARY KEY (milestone_id),
    CONSTRAINT fk_order_milestones_order FOREIGN KEY (order_id)
        REFERENCES public.orders (order_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_order_milestones_order_id
    ON public.order_payment_milestones (order_id);

