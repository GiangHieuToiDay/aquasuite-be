-- Additive migration; existing drafts and V1/V2 remain unchanged.
CREATE TABLE bundle_component_groups (
    bundle_product_id UUID NOT NULL REFERENCES bundle_configurations(bundle_product_id),
    group_id UUID NOT NULL,
    default_variant_id UUID NOT NULL REFERENCES product_variants(variant_id),
    allowed_variant_ids UUID[] NOT NULL,
    PRIMARY KEY (bundle_product_id, group_id),
    UNIQUE (bundle_product_id, default_variant_id),
    CHECK (cardinality(allowed_variant_ids) BETWEEN 1 AND 100),
    CHECK (array_position(allowed_variant_ids, NULL) IS NULL),
    CHECK (default_variant_id = ANY(allowed_variant_ids))
);
