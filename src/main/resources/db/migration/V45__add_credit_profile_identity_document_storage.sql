-- UC-24: private MinIO object key for an uploaded CCCD/CMND image.
-- No public URL or sensitive image data is stored in the relational database.
ALTER TABLE public.customer_credit_profile_requests
    ADD COLUMN IF NOT EXISTS identity_document_object_key VARCHAR(512);
