-- UC-32: chống ghi nhận trùng phiếu nhập kho khi client gửi lại cùng yêu cầu (retry sau timeout).
-- Additive: phiếu cũ giữ NULL; chỉ phiếu mới bắt buộc có khóa ở tầng ứng dụng.
ALTER TABLE public.goods_receipts
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN request_hash VARCHAR(64);

CREATE UNIQUE INDEX uq_goods_receipts_idempotency_key
    ON public.goods_receipts (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
