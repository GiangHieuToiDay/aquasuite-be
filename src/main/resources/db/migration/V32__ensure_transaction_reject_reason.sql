-- Repair forward-only schema compatibility for databases that recorded an older V15
-- before V15 was reassigned to add_reject_reason_to_transactions during the merge.
-- Fresh databases receive this column in V15; existing databases receive it here.

ALTER TABLE public.transactions
    ADD COLUMN IF NOT EXISTS reject_reason TEXT;
