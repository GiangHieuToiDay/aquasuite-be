CREATE TABLE public.inventories (
    inventory_id UUID PRIMARY KEY,
    variant_id UUID NOT NULL,
    warehouse_code VARCHAR(50) NOT NULL DEFAULT 'WH_MAIN',
    on_hand_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    reserved_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    minimum_qty NUMERIC(12,2) NOT NULL DEFAULT 0,
    maximum_qty NUMERIC(12,2),
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT uq_inventory_variant_warehouse UNIQUE (variant_id, warehouse_code),
    CONSTRAINT chk_inventory_non_negative CHECK (on_hand_qty >= 0 AND reserved_qty >= 0 AND minimum_qty >= 0)
);
CREATE INDEX idx_inv_variant_id ON public.inventories (variant_id);

CREATE TABLE public.stock_movements (
    movement_id UUID PRIMARY KEY,
    inventory_id UUID NOT NULL REFERENCES public.inventories(inventory_id),
    variant_id UUID NOT NULL,
    movement_type VARCHAR(30) NOT NULL,
    quantity NUMERIC(12,2) NOT NULL,
    balance_after NUMERIC(12,2) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100),
    note TEXT,
    created_by UUID,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_movement_type CHECK (movement_type IN ('INITIAL_IMPORT','GOODS_RECEIPT','STOCK_DISPATCH','RETURN_IN','SUPPLIER_RETURN','ADJUSTMENT_IN','ADJUSTMENT_OUT'))
);
CREATE INDEX idx_movement_variant_created ON public.stock_movements (variant_id, created_at DESC);
CREATE INDEX idx_movement_inventory_id ON public.stock_movements (inventory_id);
CREATE INDEX idx_movement_reference ON public.stock_movements (reference_type, reference_id);

CREATE TABLE public.stock_counts (
    stock_count_id UUID PRIMARY KEY,
    stock_count_code VARCHAR(50) NOT NULL UNIQUE,
    warehouse_code VARCHAR(50) NOT NULL DEFAULT 'WH_MAIN',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    counted_by UUID,
    approved_by UUID,
    count_date TIMESTAMP,
    note TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_stock_count_status CHECK (status IN ('DRAFT','PENDING_APPROVAL','COMPLETED','CANCELLED'))
);

CREATE TABLE public.stock_count_items (
    stock_count_item_id UUID PRIMARY KEY,
    stock_count_id UUID NOT NULL REFERENCES public.stock_counts(stock_count_id),
    inventory_id UUID NOT NULL REFERENCES public.inventories(inventory_id),
    variant_id UUID NOT NULL,
    system_qty NUMERIC(12,2) NOT NULL,
    actual_qty NUMERIC(12,2) NOT NULL,
    difference_qty NUMERIC(12,2) NOT NULL,
    reason VARCHAR(255)
);
CREATE INDEX idx_item_stock_count_id ON public.stock_count_items (stock_count_id);
CREATE INDEX idx_item_variant_id ON public.stock_count_items (variant_id);

CREATE TABLE public.suppliers (
    supplier_id UUID PRIMARY KEY,
    supplier_code VARCHAR(50) NOT NULL UNIQUE,
    supplier_name VARCHAR(255) NOT NULL,
    contact_name VARCHAR(150),
    phone VARCHAR(30), email VARCHAR(150), address TEXT, tax_code VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
    CONSTRAINT chk_supplier_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE public.supplier_products (
    supplier_id UUID NOT NULL REFERENCES public.suppliers(supplier_id),
    product_id UUID NOT NULL,
    supplier_sku VARCHAR(100),
    last_supply_price NUMERIC(15,2),
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (supplier_id, product_id)
);
