-- Create entity-owned tables introduced without migrations by the merged work.

CREATE TABLE public.ai_chat_sessions (
    session_id UUID NOT NULL,
    customer_id UUID,
    user_id UUID,
    session_type VARCHAR(50) NOT NULL,
    messages JSONB NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CONSTRAINT pk_ai_chat_sessions PRIMARY KEY (session_id)
);
CREATE INDEX idx_ai_chat_sessions_customer_id ON public.ai_chat_sessions (customer_id);
CREATE INDEX idx_ai_chat_sessions_user_id ON public.ai_chat_sessions (user_id);
CREATE INDEX idx_ai_chat_sessions_started_at ON public.ai_chat_sessions (started_at);

CREATE TABLE public.deliveries (
    delivery_id UUID NOT NULL,
    delivery_code VARCHAR(100) NOT NULL,
    order_id UUID NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL,
    shipper_id UUID,
    batch_no INTEGER,
    delivery_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
    dispatch_date TIMESTAMPTZ,
    scheduled_date TIMESTAMPTZ,
    delivered_at TIMESTAMPTZ,
    recipient_name VARCHAR(255) NOT NULL,
    recipient_phone VARCHAR(32) NOT NULL,
    delivery_address TEXT NOT NULL,
    cod_required BOOLEAN NOT NULL DEFAULT FALSE,
    cod_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    created_by UUID NOT NULL,
    confirmed_by UUID,
    confirmed_at TIMESTAMPTZ,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_deliveries PRIMARY KEY (delivery_id),
    CONSTRAINT uq_deliveries_code UNIQUE (delivery_code),
    CONSTRAINT fk_deliveries_order FOREIGN KEY (order_id)
        REFERENCES public.orders (order_id) ON DELETE RESTRICT
);
CREATE INDEX idx_deliveries_order_id ON public.deliveries (order_id);
CREATE INDEX idx_deliveries_warehouse_code ON public.deliveries (warehouse_code);
CREATE INDEX idx_deliveries_shipper_id ON public.deliveries (shipper_id);
CREATE INDEX idx_deliveries_delivery_status ON public.deliveries (delivery_status);

CREATE TABLE public.delivery_items (
    delivery_item_id UUID NOT NULL,
    delivery_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    inventory_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    quantity NUMERIC(12,2) NOT NULL,
    serial_numbers JSONB,
    CONSTRAINT pk_delivery_items PRIMARY KEY (delivery_item_id),
    CONSTRAINT fk_delivery_items_delivery FOREIGN KEY (delivery_id)
        REFERENCES public.deliveries (delivery_id) ON DELETE CASCADE,
    CONSTRAINT fk_delivery_items_order_item FOREIGN KEY (order_item_id)
        REFERENCES public.order_items (order_item_id) ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_items_inventory FOREIGN KEY (inventory_id)
        REFERENCES public.inventories (inventory_id) ON DELETE RESTRICT,
    CONSTRAINT fk_delivery_items_variant FOREIGN KEY (variant_id)
        REFERENCES public.product_variants (variant_id) ON DELETE RESTRICT
);
CREATE INDEX idx_delivery_items_delivery_id ON public.delivery_items (delivery_id);
CREATE INDEX idx_delivery_items_order_item_id ON public.delivery_items (order_item_id);
CREATE INDEX idx_delivery_items_inventory_id ON public.delivery_items (inventory_id);
CREATE INDEX idx_delivery_items_variant_id ON public.delivery_items (variant_id);

CREATE TABLE public.transactions (
    transaction_id UUID NOT NULL,
    transaction_code VARCHAR(100) NOT NULL,
    transaction_type VARCHAR(32) NOT NULL,
    payment_type VARCHAR(32),
    category VARCHAR(100) NOT NULL,
    customer_id UUID,
    supplier_id UUID,
    order_id UUID,
    goods_receipt_id UUID,
    invoice_id UUID,
    amount NUMERIC(15,2) NOT NULL,
    payment_method VARCHAR(32) NOT NULL,
    reference_no VARCHAR(255),
    proof_url TEXT,
    transaction_date TIMESTAMPTZ NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    description TEXT,
    created_by UUID NOT NULL,
    approved_by UUID,
    approved_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_transactions PRIMARY KEY (transaction_id),
    CONSTRAINT uq_transactions_code UNIQUE (transaction_code)
);
CREATE INDEX idx_transactions_type ON public.transactions (transaction_type);
CREATE INDEX idx_transactions_customer_id ON public.transactions (customer_id);
CREATE INDEX idx_transactions_supplier_id ON public.transactions (supplier_id);
CREATE INDEX idx_transactions_order_id ON public.transactions (order_id);
CREATE INDEX idx_transactions_goods_receipt_id ON public.transactions (goods_receipt_id);
CREATE INDEX idx_transactions_invoice_id ON public.transactions (invoice_id);
CREATE INDEX idx_transactions_status ON public.transactions (status);
CREATE INDEX idx_transactions_date ON public.transactions (transaction_date);

CREATE TABLE public.warranty_records (
    warranty_record_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    order_item_id UUID NOT NULL,
    variant_id UUID NOT NULL,
    serial_number VARCHAR(255) NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE NOT NULL,
    warranty_terms TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    CONSTRAINT pk_warranty_records PRIMARY KEY (warranty_record_id)
);
CREATE INDEX idx_warranty_records_customer_id ON public.warranty_records (customer_id);
CREATE INDEX idx_warranty_records_order_item_id ON public.warranty_records (order_item_id);
CREATE INDEX idx_warranty_records_variant_id ON public.warranty_records (variant_id);
CREATE INDEX idx_warranty_records_serial_number ON public.warranty_records (serial_number);
CREATE INDEX idx_warranty_records_status ON public.warranty_records (status);

CREATE TABLE public.technical_jobs (
    technical_job_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    order_id UUID,
    warranty_record_id UUID,
    technician_id UUID,
    created_by UUID NOT NULL,
    job_type VARCHAR(32) NOT NULL,
    service_address TEXT NOT NULL,
    issue_description TEXT NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    result TEXT,
    technical_note TEXT,
    rejection_reason TEXT,
    scheduled_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_technical_jobs PRIMARY KEY (technical_job_id)
);
CREATE INDEX idx_technical_jobs_customer_id ON public.technical_jobs (customer_id);
CREATE INDEX idx_technical_jobs_order_id ON public.technical_jobs (order_id);
CREATE INDEX idx_technical_jobs_warranty_record_id ON public.technical_jobs (warranty_record_id);
CREATE INDEX idx_technical_jobs_technician_id ON public.technical_jobs (technician_id);
CREATE INDEX idx_technical_jobs_job_type ON public.technical_jobs (job_type);
CREATE INDEX idx_technical_jobs_status ON public.technical_jobs (status);
CREATE INDEX idx_technical_jobs_scheduled_at ON public.technical_jobs (scheduled_at);
