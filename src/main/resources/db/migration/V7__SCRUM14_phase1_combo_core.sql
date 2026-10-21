-- Flyway owns the transaction for preflight, DDL, backfill and verification.
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

-- Run only through Flyway; do not apply directly.
CREATE TABLE bundle_configurations (
 bundle_product_id UUID PRIMARY KEY REFERENCES products(product_id),
 package_price NUMERIC(15,2), valid_from TIMESTAMPTZ, valid_until TIMESTAMPTZ,
 publication_status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
 stock_policy VARCHAR(40) NOT NULL DEFAULT 'CHECK_COMPONENT_STOCK',
 version BIGINT NOT NULL DEFAULT 0,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT bundle_price_positive CHECK(package_price IS NULL OR package_price>0),
 CONSTRAINT bundle_validity CHECK ((valid_from IS NULL AND valid_until IS NULL) OR
   (valid_from IS NOT NULL AND valid_until IS NOT NULL AND valid_from<valid_until)),
 CONSTRAINT bundle_publication CHECK(publication_status IN ('DRAFT','PUBLISHED','UNPUBLISHED')),
 CONSTRAINT bundle_published_complete CHECK(publication_status<>'PUBLISHED' OR
   (package_price IS NOT NULL AND valid_from IS NOT NULL AND valid_until IS NOT NULL)),
 CONSTRAINT bundle_version_positive CHECK(version>=0)
);
INSERT INTO bundle_configurations(bundle_product_id)
 SELECT product_id FROM products WHERE product_type='BUNDLE';
-- Quantity, uniqueness and item foreign keys are already enforced by V4.
CREATE INDEX uc13_items_parent_idx ON bundle_items(bundle_product_id);
CREATE INDEX uc13_publication_idx ON bundle_configurations(publication_status);
-- Business audit is owned by audit_logs (V3.1); no unused audit_entries table.

DO $$
BEGIN
 IF EXISTS (SELECT 1 FROM products p LEFT JOIN bundle_configurations c ON c.bundle_product_id=p.product_id
            WHERE p.product_type='BUNDLE' AND c.bundle_product_id IS NULL)
 OR EXISTS (SELECT 1 FROM bundle_configurations c JOIN products p ON p.product_id=c.bundle_product_id WHERE p.product_type<>'BUNDLE')
 THEN RAISE EXCEPTION 'UC13 verification failed: bundle configuration mapping is incomplete';
 END IF;
END $$;
SELECT 'UC13 schema verification passed' AS result;
