CREATE TABLE cod_collections (
    collection_id UUID PRIMARY KEY,
    delivery_id UUID NOT NULL REFERENCES deliveries(delivery_id) ON DELETE RESTRICT,
    order_id UUID NOT NULL REFERENCES orders(order_id) ON DELETE RESTRICT,
    customer_id UUID NOT NULL REFERENCES customers(customer_id) ON DELETE RESTRICT,
    shipper_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    delivery_code VARCHAR(100) NOT NULL,
    expected_amount NUMERIC(15,2) NOT NULL CHECK (expected_amount > 0),
    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0 AND amount = trunc(amount)),
    review_status VARCHAR(16) NOT NULL DEFAULT 'SUBMITTED'
        CHECK (review_status IN ('SUBMITTED','ACCEPTED','REJECTED')),
    collected_at TIMESTAMPTZ NOT NULL,
    submitted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    reviewed_by UUID REFERENCES users(id) ON DELETE RESTRICT,
    reviewed_at TIMESTAMPTZ,
    reject_reason VARCHAR(1000),
    note VARCHAR(1000),
    proof_type VARCHAR(32) NOT NULL CHECK (proof_type IN ('image/png','image/jpeg')),
    proof_content BYTEA NOT NULL CHECK (octet_length(proof_content) BETWEEN 1 AND 5242880),
    idempotency_key UUID NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CHECK (review_status = 'SUBMITTED' OR (reviewed_by IS NOT NULL AND reviewed_at IS NOT NULL)),
    CHECK (reviewed_by IS NULL OR reviewed_by <> shipper_id),
    CHECK (review_status <> 'REJECTED' OR length(trim(reject_reason)) > 0)
);
CREATE INDEX idx_cod_collections_shipper ON cod_collections(shipper_id, submitted_at);
CREATE INDEX idx_cod_collections_review ON cod_collections(review_status, submitted_at);
CREATE INDEX idx_cod_collections_order ON cod_collections(order_id);
CREATE INDEX idx_cod_collections_customer ON cod_collections(customer_id);
CREATE INDEX idx_cod_collections_delivery ON cod_collections(delivery_id);

CREATE TABLE cod_remittances (
    remittance_id UUID PRIMARY KEY,
    shipper_id UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    received_by UUID NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    received_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0 AND amount = trunc(amount)),
    note VARCHAR(1000),
    idempotency_key UUID NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    CHECK (shipper_id <> received_by)
);
CREATE INDEX idx_cod_remittances_shipper ON cod_remittances(shipper_id, received_at);
CREATE TABLE cod_remittance_items (
    remittance_id UUID NOT NULL REFERENCES cod_remittances(remittance_id) ON DELETE RESTRICT,
    collection_id UUID NOT NULL REFERENCES cod_collections(collection_id) ON DELETE RESTRICT,
    transaction_id UUID NOT NULL UNIQUE REFERENCES transactions(transaction_id) ON DELETE RESTRICT,
    amount NUMERIC(15,2) NOT NULL CHECK (amount > 0 AND amount = trunc(amount)),
    PRIMARY KEY (remittance_id, collection_id)
);
CREATE INDEX idx_cod_items_collection ON cod_remittance_items(collection_id);

-- All order writers hold the row lock; prevent changes from orphaning open COD obligations.
CREATE FUNCTION guard_open_cod_order() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    IF (NEW.total_amount IS DISTINCT FROM OLD.total_amount
        OR NEW.customer_id IS DISTINCT FROM OLD.customer_id
        OR (NEW.order_status IS DISTINCT FROM OLD.order_status AND NEW.order_status IN ('CANCELLED','PENDING_CANCEL')))
        AND EXISTS (
            SELECT 1 FROM cod_collections c WHERE c.order_id = OLD.order_id AND
            (c.review_status = 'SUBMITTED' OR (c.review_status = 'ACCEPTED' AND c.amount >
                COALESCE((SELECT sum(i.amount) FROM cod_remittance_items i WHERE i.collection_id=c.collection_id),0)))
        ) THEN
        RAISE EXCEPTION 'Reconcile open COD before changing this order' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;
CREATE TRIGGER guard_open_cod_order BEFORE UPDATE ON orders
    FOR EACH ROW EXECUTE FUNCTION guard_open_cod_order();
