-- V19: Thêm cột credit_request_id để liên kết đơn hàng DRAFT với yêu cầu duyệt hạn mức công nợ UC-24
ALTER TABLE public.orders 
    ADD COLUMN IF NOT EXISTS credit_request_id UUID,
    ADD CONSTRAINT fk_orders_credit_request FOREIGN KEY (credit_request_id)
        REFERENCES public.customer_credit_profile_requests (request_id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_orders_credit_request_id ON public.orders (credit_request_id);

