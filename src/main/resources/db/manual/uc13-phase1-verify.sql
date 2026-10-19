DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM products p LEFT JOIN bundle_configurations c ON c.bundle_product_id=p.product_id
            WHERE p.product_type='BUNDLE' AND c.bundle_product_id IS NULL)
 OR EXISTS (SELECT 1 FROM bundle_configurations c JOIN products p ON p.product_id=c.bundle_product_id WHERE p.product_type<>'BUNDLE')
 THEN RAISE EXCEPTION 'UC13 verification failed: bundle configuration mapping is incomplete';
 END IF;
END $$;
SELECT 'UC13 schema verification passed' AS result;
