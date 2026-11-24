-- FE-13: hủy công việc kỹ thuật (Chủ cửa hàng hủy, hoặc tự hủy khi đơn hàng nguồn bị hủy).
-- Additive: cột nullable, dữ liệu cũ không đổi. Trạng thái CANCELLED dùng cột status VARCHAR sẵn có (không có CHECK).
ALTER TABLE public.technical_jobs
    ADD COLUMN cancellation_reason TEXT,
    ADD COLUMN cancelled_at TIMESTAMPTZ;
