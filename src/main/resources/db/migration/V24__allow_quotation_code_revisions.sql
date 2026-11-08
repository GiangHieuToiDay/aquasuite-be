-- Existing uq_quotations_code_revision still rejects duplicate (code, revision) pairs.
ALTER TABLE public.quotations DROP CONSTRAINT IF EXISTS uq_quotations_code;
