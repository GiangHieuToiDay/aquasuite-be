-- Loại bỏ hoàn toàn công việc kỹ thuật (nhóm quyết định 2026-11-24/25: bỏ role Nhân viên kỹ thuật và FE-13).
-- Sau V53 (xóa role TECHNICAL_STAFF), code không còn đọc hay ghi bảng technical_jobs và cột warranty_tickets.technical_job_id.
-- LƯU Ý: lệnh này xóa vĩnh viễn dữ liệu công việc kỹ thuật cũ; sao lưu trước khi chạy trên môi trường có dữ liệu thật.

ALTER TABLE public.warranty_tickets
    DROP COLUMN IF EXISTS technical_job_id;

DROP TABLE IF EXISTS public.technical_jobs;
