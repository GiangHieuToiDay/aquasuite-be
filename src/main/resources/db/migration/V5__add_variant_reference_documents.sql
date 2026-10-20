-- UC-10: tables required to retain variant usage history and clear abandoned carts.
-- V1-V4 remain immutable; these tables are additive.

CREATE TABLE public.goods_receipt_items (
    goods_receipt_item_id UUID PRIMARY KEY,
    goods_receipt_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_cost NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(15,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_goods_receipt_items_variant ON public.goods_receipt_items(variant_id);

CREATE TABLE public.quotation_items (
    quotation_item_id UUID PRIMARY KEY,
    quotation_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(15,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_quotation_items_variant ON public.quotation_items(variant_id);

CREATE TABLE public.order_items (
    order_item_id UUID PRIMARY KEY,
    order_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(15,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_order_items_variant ON public.order_items(variant_id);

CREATE TABLE public.stock_dispatch_items (
    dispatch_item_id UUID PRIMARY KEY,
    dispatch_id UUID,
    order_item_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    quantity NUMERIC(12,2) NOT NULL,
    serial_numbers JSONB
);
CREATE INDEX idx_stock_dispatch_items_variant ON public.stock_dispatch_items(variant_id);

CREATE TABLE public.sales_invoice_items (
    invoice_item_id UUID PRIMARY KEY,
    invoice_id UUID,
    order_item_id UUID,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    description TEXT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL DEFAULT 0,
    discount_amount NUMERIC(15,2) NOT NULL DEFAULT 0,
    tax_rate NUMERIC(5,2) NOT NULL DEFAULT 0,
    line_total NUMERIC(15,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_sales_invoice_items_variant ON public.sales_invoice_items(variant_id);

CREATE TABLE public.carts (
    cart_id UUID PRIMARY KEY,
    customer_id UUID,
    status VARCHAR(30),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE public.cart_items (
    cart_item_id UUID PRIMARY KEY,
    cart_id UUID REFERENCES public.carts(cart_id) ON DELETE CASCADE,
    variant_id UUID NOT NULL REFERENCES public.product_variants(variant_id) ON DELETE RESTRICT,
    quantity NUMERIC(12,2) NOT NULL,
    unit_price NUMERIC(15,2) NOT NULL DEFAULT 0
);
CREATE INDEX idx_cart_items_variant ON public.cart_items(variant_id);
