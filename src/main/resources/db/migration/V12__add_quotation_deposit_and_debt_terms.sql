-- V7: Add deposit and credit debt terms to quotations table

ALTER TABLE public.quotations ADD COLUMN IF NOT EXISTS deposit_percent NUMERIC(5,2);
ALTER TABLE public.quotations ADD COLUMN IF NOT EXISTS deposit_amount NUMERIC(15,2);
ALTER TABLE public.quotations ADD COLUMN IF NOT EXISTS credit_term_days INT;
ALTER TABLE public.quotations ADD COLUMN IF NOT EXISTS remaining_debt_amount NUMERIC(15,2);
ALTER TABLE public.quotations ADD COLUMN IF NOT EXISTS debt_condition TEXT;

