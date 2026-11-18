-- FE13-WARRANTY-DISPATCH: liên kết 2 chiều giữa Công việc kỹ thuật và Yêu cầu bảo hành (ticket).
-- Phía warranty_tickets.technical_job_id đã tồn tại từ V32; bổ sung chiều ngược trên technical_jobs
-- để job biết nó phục vụ ticket nào (phục vụ phản hồi job COMPLETED → ticket DONE và idempotent dispatch).

ALTER TABLE public.technical_jobs
    ADD COLUMN warranty_ticket_id UUID;

CREATE INDEX idx_technical_jobs_warranty_ticket_id
    ON public.technical_jobs (warranty_ticket_id);
