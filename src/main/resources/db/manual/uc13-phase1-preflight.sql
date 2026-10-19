DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM bundle_items WHERE quantity IS NULL OR quantity <= 0)
 OR EXISTS (SELECT 1 FROM bundle_items GROUP BY bundle_product_id,variant_id HAVING count(*)>1)
 OR EXISTS (SELECT 1 FROM bundle_items b LEFT JOIN products p ON p.product_id=b.bundle_product_id
            LEFT JOIN product_variants v ON v.variant_id=b.variant_id
            LEFT JOIN products vp ON vp.product_id=v.product_id
            WHERE p.product_id IS NULL OR p.product_type<>'BUNDLE' OR v.variant_id IS NULL OR vp.product_id IS NULL OR vp.product_type<>'NORMAL')
 OR EXISTS (SELECT 1 FROM product_variants v JOIN products p ON p.product_id=v.product_id WHERE p.product_type='BUNDLE')
 THEN RAISE EXCEPTION 'UC13 preflight failed: invalid, duplicate, orphan or nested bundle data. Correct reviewed data before retry.';
 END IF;
END $$;
