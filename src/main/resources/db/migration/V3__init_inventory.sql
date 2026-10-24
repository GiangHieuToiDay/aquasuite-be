-- ====================================================================
-- V3: Module Inventory
-- Entities:
--   - Supplier (suppliers)
--   - SupplierProduct (supplier_products)
--   - Inventory (inventories)
--   - StockMovement (stock_movements)
--   - StockCount (stock_counts)
--   - StockCountItem (stock_count_items)
-- ====================================================================

-- 1. Suppliers
CREATE TABLE IF NOT EXISTS suppliers (
    supplier_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_code VARCHAR(50) NOT NULL UNIQUE,
    supplier_name VARCHAR(255) NOT NULL,
    contact_name VARCHAR(150),
    phone VARCHAR(30),
    email VARCHAR(150),
    address TEXT,
    tax_code VARCHAR(50),
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_supplier_code ON suppliers(supplier_code);

-- 2. Supplier Products (Composite Key)
CREATE TABLE IF NOT EXISTS supplier_products (
    supplier_id UUID NOT NULL REFERENCES suppliers(supplier_id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES products(product_id) ON DELETE CASCADE,
    supplier_sku VARCHAR(100),
    last_supply_price NUMERIC(15, 2),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (supplier_id, product_id)
);

-- 3. Inventories
CREATE TABLE IF NOT EXISTS inventories (
    inventory_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id UUID NOT NULL REFERENCES product_variants(variant_id) ON DELETE CASCADE,
    warehouse_code VARCHAR(50) NOT NULL DEFAULT 'WH_MAIN',
    on_hand_qty NUMERIC(12, 2) NOT NULL DEFAULT 0,
    reserved_qty NUMERIC(12, 2) NOT NULL DEFAULT 0,
    minimum_qty NUMERIC(12, 2) NOT NULL DEFAULT 0,
    maximum_qty NUMERIC(12, 2),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT idx_inv_variant_wh UNIQUE (variant_id, warehouse_code)
);

CREATE INDEX IF NOT EXISTS idx_inv_variant_id ON inventories(variant_id);

-- 4. Stock Movements (Immutable Ledger)
CREATE TABLE IF NOT EXISTS stock_movements (
    movement_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_id UUID NOT NULL REFERENCES inventories(inventory_id) ON DELETE RESTRICT,
    variant_id UUID NOT NULL REFERENCES product_variants(variant_id) ON DELETE RESTRICT,
    movement_type VARCHAR(30) NOT NULL,
    quantity NUMERIC(12, 2) NOT NULL,
    balance_after NUMERIC(12, 2) NOT NULL,
    reference_type VARCHAR(50) NOT NULL,
    reference_id VARCHAR(100),
    note TEXT,
    created_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_movement_variant_created ON stock_movements(variant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_movement_inventory_id ON stock_movements(inventory_id);
CREATE INDEX IF NOT EXISTS idx_movement_reference ON stock_movements(reference_type, reference_id);

-- 5. Stock Counts
CREATE TABLE IF NOT EXISTS stock_counts (
    stock_count_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_count_code VARCHAR(50) NOT NULL UNIQUE,
    warehouse_code VARCHAR(50) NOT NULL DEFAULT 'WH_MAIN',
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    counted_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    approved_by BIGINT REFERENCES users(id) ON DELETE SET NULL,
    count_date TIMESTAMPTZ,
    note TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_stock_count_code ON stock_counts(stock_count_code);

-- 6. Stock Count Items
CREATE TABLE IF NOT EXISTS stock_count_items (
    stock_count_item_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    stock_count_id UUID NOT NULL REFERENCES stock_counts(stock_count_id) ON DELETE CASCADE,
    inventory_id UUID NOT NULL REFERENCES inventories(inventory_id) ON DELETE RESTRICT,
    variant_id UUID NOT NULL REFERENCES product_variants(variant_id) ON DELETE RESTRICT,
    system_qty NUMERIC(12, 2) NOT NULL,
    actual_qty NUMERIC(12, 2) NOT NULL,
    difference_qty NUMERIC(12, 2) NOT NULL,
    reason VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_item_stock_count_id ON stock_count_items(stock_count_id);
CREATE INDEX IF NOT EXISTS idx_item_variant_id ON stock_count_items(variant_id);
