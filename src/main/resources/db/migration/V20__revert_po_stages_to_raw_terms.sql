-- Migration V20: Revert PO stages to raw note terms

-- 1. Drop stage rollback requests and stage tables
DROP TABLE IF EXISTS public.purchase_order_stage_rollback_requests CASCADE;
DROP TABLE IF EXISTS public.purchase_order_stages CASCADE;

-- 2. Drop index and stage specific columns on purchase_orders
DROP INDEX IF EXISTS idx_po_is_short_closed;

ALTER TABLE public.purchase_orders
    DROP COLUMN IF EXISTS delivery_plan_type,
    DROP COLUMN IF EXISTS paid_amount,
    DROP COLUMN IF EXISTS is_short_closed,
    DROP COLUMN IF EXISTS reopened_by,
    DROP COLUMN IF EXISTS reopened_at,
    DROP COLUMN IF EXISTS reopen_reason;

