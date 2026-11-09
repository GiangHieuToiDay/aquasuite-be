-- V14: Ensure customers.delivery_addresses column exists
-- Fix for databases migrated before delivery_addresses was added to the V1 baseline.

ALTER TABLE public.customers
    ADD COLUMN IF NOT EXISTS delivery_addresses JSONB NOT NULL DEFAULT '[]'::jsonb;

CREATE INDEX IF NOT EXISTS idx_customers_delivery_addresses
    ON public.customers USING gin (delivery_addresses);
