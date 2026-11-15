-- Preserve applied V35/V36 history. UC36 completion, including zero-release decisions,
-- now reuses the existing Sales operation ledger; physical line history stays in Inventory.
-- A request-ID collision fails the migration transaction instead of overwriting history.
INSERT INTO public.sales_reservation_operations(request_id, order_id, actor_id, created_at, payload)
SELECT decision_id, order_id, executed_by, executed_at, 'UC36_RELEASE:' || result::text
FROM public.sales_reservation_releases;

DROP TABLE public.sales_reservation_releases;
