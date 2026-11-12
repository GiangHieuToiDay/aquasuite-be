-- V15: UC-70 — lưu lý do từ chối khi kế toán từ chối một khoản chuyển khoản đang chờ.
-- transactions.status không có CHECK constraint nên REJECTED lưu được sẵn; chỉ cần thêm cột lý do.
-- approved_by / approved_at được tái sử dụng làm người và thời điểm ra quyết định (verify/reject).

ALTER TABLE public.transactions ADD COLUMN IF NOT EXISTS reject_reason TEXT;
