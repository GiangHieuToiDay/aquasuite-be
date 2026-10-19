-- Executed within the runner's transaction and advisory lock. Do not apply directly.
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
ALTER TABLE bundle_items ADD CONSTRAINT uc13_bundle_quantity_positive CHECK(quantity>0);
ALTER TABLE bundle_items ADD CONSTRAINT uc13_bundle_item_unique UNIQUE(bundle_product_id,variant_id);
ALTER TABLE bundle_items ADD CONSTRAINT uc13_bundle_parent_fk FOREIGN KEY(bundle_product_id) REFERENCES products(product_id);
ALTER TABLE bundle_items ADD CONSTRAINT uc13_bundle_variant_fk FOREIGN KEY(variant_id) REFERENCES product_variants(variant_id);
CREATE INDEX uc13_items_parent_idx ON bundle_items(bundle_product_id);
CREATE INDEX uc13_publication_idx ON bundle_configurations(publication_status);
CREATE TABLE audit_entries (
 id UUID PRIMARY KEY, actor_id BIGINT NOT NULL, action VARCHAR(40) NOT NULL,
 aggregate_type VARCHAR(40) NOT NULL, aggregate_id UUID NOT NULL,
 before_value JSONB, after_value JSONB, occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX uc13_audit_aggregate_idx ON audit_entries(aggregate_id,occurred_at);
