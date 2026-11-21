package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.response.VariantDocumentUsage;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SalesVariantUsageService  {
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public VariantDocumentUsage getVariantDocumentUsage(UUID variantId) {
        return new VariantDocumentUsage(variantId,
                count("goods_receipt_items", variantId), count("stock_dispatch_items", variantId),
                count("quotation_items", variantId), count("order_items", variantId),
                count("sales_invoice_items", variantId), count("purchase_order_items", variantId),
                count("delivery_items", variantId), count("cart_items", variantId));
    }

    @Transactional
    public void removeVariantFromCarts(UUID variantId) {
        jdbcTemplate.update("DELETE FROM public.cart_items WHERE variant_id = ?", variantId);
    }

    private long count(String table, UUID variantId) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public." + table + " WHERE variant_id = ?", Long.class, variantId);
        return result == null ? 0 : result;
    }
}
