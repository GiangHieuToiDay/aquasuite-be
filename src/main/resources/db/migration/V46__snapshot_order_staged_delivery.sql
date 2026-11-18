ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS is_staged_delivery BOOLEAN NOT NULL DEFAULT FALSE;

-- Preserve the known intent of historical orders that already have multiple delivery-batch invoices.
UPDATE orders o
SET is_staged_delivery = TRUE
WHERE EXISTS (
    SELECT 1
    FROM sales_invoices si
    WHERE si.order_id = o.order_id
    GROUP BY si.order_id
    HAVING COUNT(*) > 1
);
