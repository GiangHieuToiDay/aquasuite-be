-- V4__add_paid_amount_to_orders.sql
-- Add paid_amount to orders table to track deposits and payments directly

-- 1. Thêm cột paid_amount với giá trị mặc định là 0.00
ALTER TABLE public.orders 
    ADD COLUMN IF NOT EXISTS paid_amount NUMERIC(15, 2) NOT NULL DEFAULT 0.00;

COMMENT ON COLUMN public.orders.paid_amount IS 'Số tiền khách đã thanh toán hoặc tiền cọc đã thu';

-- 2. Đồng bộ (Backfill) dữ liệu từ bảng transactions cho các đơn hàng cũ nếu đã phát sinh giao dịch
UPDATE public.orders o
SET paid_amount = COALESCE((
    SELECT SUM(t.amount)
    FROM public.transactions t
    WHERE t.order_id = o.order_id 
      AND (t.status = 'VERIFIED' OR t.status = 'COMPLETED')
), 0.00)
WHERE EXISTS (
    SELECT 1 FROM public.transactions t WHERE t.order_id = o.order_id
);
