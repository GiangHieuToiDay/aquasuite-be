-- Schema before SCRUM-14 combo migration. No seed data or credentials.


    create table brands (
        created_at timestamp(6),
        updated_at timestamp(6),
        brand_id uuid not null,
        status varchar(50) not null check (status in ('ACTIVE','INACTIVE')),
        brand_code varchar(100) not null unique,
        brand_name varchar(255) not null,
        description TEXT,
        primary key (brand_id)
    );


    create table bundle_items (
        quantity integer not null,
        created_at timestamp(6),
        bundle_item_id uuid not null,
        bundle_product_id uuid not null,
        variant_id uuid not null,
        primary key (bundle_item_id)
    );

    create table categories (
        created_at timestamp(6),
        updated_at timestamp(6),
        category_id uuid not null,
        parent_id uuid,
        status varchar(50) not null check (status in ('ACTIVE','INACTIVE')),
        category_code varchar(100) not null unique,
        category_name varchar(255) not null,
        description TEXT,
        primary key (category_id)
    );

    create table inventories (
        maximum_qty numeric(12,2),
        minimum_qty numeric(12,2) not null,
        on_hand_qty numeric(12,2) not null,
        reserved_qty numeric(12,2) not null,
        updated_at timestamp(6) not null,
        inventory_id uuid not null,
        variant_id uuid not null,
        warehouse_code varchar(50) not null,
        primary key (inventory_id),
        constraint idx_inv_variant_wh unique (variant_id, warehouse_code)
    );

    create table product_images (
        is_primary boolean not null,
        sort_order integer not null,
        created_at timestamp(6),
        image_id uuid not null,
        product_id uuid not null,
        variant_id uuid,
        image_url TEXT not null,
        primary key (image_id)
    );

    create table product_variants (
        conversion_rate numeric(12,4) not null,
        cost_price numeric(15,2) not null,
        selling_price numeric(15,2) not null,
        serial_tracking boolean not null,
        weight numeric(10,3) not null,
        created_at timestamp(6),
        updated_at timestamp(6),
        product_id uuid not null,
        variant_id uuid not null,
        warranty_policy_id uuid,
        status varchar(50) not null check (status in ('ACTIVE','INACTIVE','DELETED')),
        unit varchar(50) not null,
        color varchar(100),
        size varchar(100),
        sku varchar(100) not null unique,
        variant_name varchar(255) not null,
        technical_spec jsonb,
        primary key (variant_id)
    );

    create table products (
        created_at timestamp(6),
        updated_at timestamp(6),
        brand_id uuid,
        category_id uuid not null,
        product_id uuid not null,
        product_type varchar(50) not null check (product_type in ('NORMAL','BUNDLE')),
        status varchar(50) not null check (status in ('ACTIVE','INACTIVE','DELETED')),
        product_code varchar(100) not null unique,
        description TEXT,
        product_name varchar(255) not null,
        specifications jsonb,
        primary key (product_id)
    );

    create table stock_count_items (
        actual_qty numeric(12,2) not null,
        difference_qty numeric(12,2) not null,
        system_qty numeric(12,2) not null,
        inventory_id uuid not null,
        stock_count_id uuid not null,
        stock_count_item_id uuid not null,
        variant_id uuid not null,
        reason varchar(255),
        primary key (stock_count_item_id)
    );

    create table stock_counts (
        approved_by bigint,
        count_date timestamp(6),
        counted_by bigint,
        created_at timestamp(6) not null,
        updated_at timestamp(6) not null,
        stock_count_id uuid not null,
        status varchar(30) not null check (status in ('DRAFT','PENDING_APPROVAL','COMPLETED','CANCELLED')),
        stock_count_code varchar(50) not null unique,
        warehouse_code varchar(50) not null,
        note TEXT,
        primary key (stock_count_id)
    );

    create table stock_movements (
        balance_after numeric(12,2) not null,
        quantity numeric(12,2) not null,
        created_at timestamp(6) not null,
        created_by bigint,
        inventory_id uuid not null,
        movement_id uuid not null,
        variant_id uuid not null,
        movement_type varchar(30) not null check (movement_type in ('INITIAL_IMPORT','GOODS_RECEIPT','STOCK_DISPATCH','RETURN_IN','SUPPLIER_RETURN','ADJUSTMENT_IN','ADJUSTMENT_OUT')),
        reference_type varchar(50) not null,
        reference_id varchar(100),
        note TEXT,
        primary key (movement_id)
    );

    create table supplier_products (
        last_supply_price numeric(15,2),
        created_at timestamp(6) not null,
        product_id uuid not null,
        supplier_id uuid not null,
        supplier_sku varchar(100),
        primary key (product_id, supplier_id)
    );

    create table suppliers (
        created_at timestamp(6) not null,
        updated_at timestamp(6) not null,
        supplier_id uuid not null,
        phone varchar(30),
        status varchar(30) not null check (status in ('ACTIVE','INACTIVE')),
        supplier_code varchar(50) not null unique,
        tax_code varchar(50),
        contact_name varchar(150),
        email varchar(150),
        address TEXT,
        supplier_name varchar(255) not null,
        primary key (supplier_id)
    );

    create table users (
        email_sent_count integer,
        email_sent_date date,
        is_verified boolean,
        created_at timestamp(6),
        id bigint generated by default as identity,
        password_last_changed_at timestamp(6),
        permission_id bigint,
        role_id bigint,
        verification_expired_at timestamp(6),
        phone varchar(20),
        status varchar(50) check (status in ('PENDING','ACTIVE','INACTIVE','DELETED')),
        username varchar(100),
        verification_code varchar(100),
        email varchar(150) unique,
        avatar varchar(255),
        fcm_token TEXT,
        password varchar(255),
        provider_id varchar(255),
        primary key (id)
    );

    create index idx_inv_variant_id 
       on inventories (variant_id);

    create index idx_item_stock_count_id 
       on stock_count_items (stock_count_id);

    create index idx_item_variant_id 
       on stock_count_items (variant_id);

    create index idx_movement_variant_created 
       on stock_movements (variant_id, created_at desc);

    create index idx_movement_inventory_id 
       on stock_movements (inventory_id);

    create index idx_movement_reference 
       on stock_movements (reference_type, reference_id);
