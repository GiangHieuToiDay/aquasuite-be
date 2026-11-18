-- FE13-WARRANTY-DISPATCH (C+): lưu vết khách chủ động xác nhận gửi yêu cầu dù thẻ bảo hành đã hết hạn.
-- Phục vụ chủ cửa hàng/audit truy được đơn nào khách đã đồng ý (hữu ích khi có tranh chấp phí về sau).

ALTER TABLE public.warranty_tickets
    ADD COLUMN expired_acknowledged BOOLEAN NOT NULL DEFAULT FALSE;
