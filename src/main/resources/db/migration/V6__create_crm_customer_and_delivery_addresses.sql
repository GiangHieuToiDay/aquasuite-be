-- V6__create_crm_customer_and_delivery_addresses.sql
-- SCRUM-5 / UC-03: Manage Profile and Delivery Addresses

-- 1. Bổ sung trường full_name vào bảng users (cho tài khoản nhân viên & định danh cá nhân)
ALTER TABLE public.users
    ADD COLUMN IF NOT EXISTS full_name VARCHAR(150);

-- 2. Bảng customers: Hồ sơ khách hàng thương mại (Bảng 4 trong bản thiết kế)
CREATE TABLE IF NOT EXISTS public.customers (
    customer_id UUID NOT NULL,
    user_id UUID UNIQUE,
    customer_code VARCHAR(50) NOT NULL UNIQUE,
    customer_name VARCHAR(150) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    email VARCHAR(320),
    customer_type VARCHAR(32) NOT NULL DEFAULT 'RETAIL', -- RETAIL, CONTRACTOR, INSTALLER, PROJECT_CUSTOMER
    tax_code VARCHAR(50),
    company_name VARCHAR(255),
    address TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customers PRIMARY KEY (customer_id),
    CONSTRAINT fk_customers_user FOREIGN KEY (user_id) REFERENCES public.users(id) ON DELETE SET NULL,
    CONSTRAINT chk_customer_type CHECK (customer_type IN ('RETAIL', 'CONTRACTOR', 'INSTALLER', 'PROJECT_CUSTOMER')),
    CONSTRAINT chk_customer_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'BLOCKED'))
);

CREATE INDEX IF NOT EXISTS idx_customers_user_id ON public.customers (user_id);
CREATE INDEX IF NOT EXISTS idx_customers_phone ON public.customers (phone);

-- 3. Bảng customer_addresses: Sổ địa chỉ nhận hàng (Bảng 7 trong bản thiết kế)
CREATE TABLE IF NOT EXISTS public.customer_addresses (
    address_id UUID NOT NULL,
    customer_id UUID NOT NULL,
    recipient_name VARCHAR(100) NOT NULL,
    phone VARCHAR(32) NOT NULL,
    province VARCHAR(100) NOT NULL,
    district VARCHAR(100) NOT NULL,
    ward VARCHAR(100) NOT NULL,
    street_address VARCHAR(255) NOT NULL,
    address_text VARCHAR(500) NOT NULL,
    label VARCHAR(50) NOT NULL DEFAULT 'Home', -- Home, Construction Site, Villa, Office, etc.
    is_default BOOLEAN NOT NULL DEFAULT false,
    is_deleted BOOLEAN NOT NULL DEFAULT false,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT pk_customer_addresses PRIMARY KEY (address_id),
    CONSTRAINT fk_customer_addresses_customer FOREIGN KEY (customer_id) REFERENCES public.customers(customer_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_customer_addresses_customer ON public.customer_addresses (customer_id, is_deleted);
CREATE INDEX IF NOT EXISTS idx_customer_addresses_default ON public.customer_addresses (customer_id, is_default) WHERE is_default = true AND is_deleted = false;
